/**
 * Copyright (c) 2005 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.webservices;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.extern.slf4j.Slf4j;


/**
 * Web service endpoints to support obtaining a Sakai session by using a Sakai user ID and a shared
 * portal secret.
 * <p>
 * <em>Note: also see {@link https://confluence.sakaiproject.org/display/DOC/Sakai+Admin+Guide+-+Advanced+Configuration+Topics}</em>
 * </p>
 * <p>
 * These web service endpoints can be used to login as a specific user by supplying a Sakai user ID
 * with a shared key rather than a password. This approach to authentication can be useful when the
 * user authenticates with an external provider such as CAS. This can allow external portals to
 * retrieve information from Sakai.
 * <p>
 * The parameters of interest when configuration this web service are:
 * <table>
 * <th>
 * <td colspan="2">Configuration Properties</td>
 * </th>
 * <tr>
 * <td>webservices.allowlogin</td>
 * <td>Set to true to allow logging in through web services.</td>
 * </tr>
 * <tr>
 * <td>webservice.portalsecret</td>
 * <td>The portal secret that is shared to external integration points.</td>
 * </tr>
 * <tr>
 * <td>session.parameter.allow</td>
 * <td>Set to true in your properties file to enable this web service to work without a
 *     password.</td>
 * </tr>
 * </table>
 *
 * <table>
 * <th>
 * <td colspan="2">Request Parameters</td>
 * </th>
 * <tr>
 * <td>sakai.session</td>
 * <td>Set as a <strong>request</strong> parameter to the value returned from SakaiPortalLogin.login
 *     or SakaiPortalLogin.loginAndCreate</td>
 * </tr>
 * </table>
 * </p>
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
@Slf4j
public class SakaiPortalLogin extends AbstractWebService {

    private User getSakaiUser(String id) {
        User user = null ;

        try {
            user = userDirectoryService.getUserByEid(id);
        } catch (Exception e) {
            user = null;
        }
        return user;
    }

    /**
     * Login to an existing Sakai account with the Sakai user ID and shared portal secret. If the
     * account doesn't exist it will be created, if user information differs from the existing account it will be updated.
     *
     * @param id Sakai user ID.
     * @param pw Shared portal secret.
     * @param firstName The first name to use when creating or updating the account.
     * @param lastName The last name to use when creating or updating the account.
     * @param eMail The email to use when creating or updating that account.
     * @return Session ID of successful login.
     * @ if there are any problems logging in.
     */
    @WebMethod
    @Path("/loginAndCreateOrUpdate")
    @Produces("text/plain")
    @GET
    public String loginAndCreate (
    		@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @WebParam(name = "id", partName = "id") @QueryParam("id") String id,
            @WebParam(name = "firstName", partName = "firstName") @QueryParam("firstName") String firstName,
            @WebParam(name = "lastName", partName = "lastName") @QueryParam("lastName") String lastName,
            @WebParam(name = "eMail", partName = "eMail") @QueryParam("eMail") String eMail) {

        String ipAddress = validateUserAddRequest(authorization);

        User user = getSakaiUser(id);
        boolean userExists = false;
        
        if ( user == null && StringUtils.isNoneBlank(firstName, lastName, eMail)) {
            log.error("Creating Sakai Account...");
            try {
            	String hiddenPW = idManager.createUuid();
                userDirectoryService.addUser(null, id, firstName, lastName, eMail, hiddenPW, "registered", null);
                log.debug("User Created...");
            } catch(Exception e) {
                log.error("Unable to create user...");
                    throw new WebApplicationException("Failed login", Response.Status.FORBIDDEN);
            }
            user = getSakaiUser(id);
            log.error("User created: {}", user); 
        }
        else if(user != null) {
        	userExists = true;
        }

        log.info("User is: {}", user);
        
        if ( user != null ) {
            Session s = sessionManager.startSession();
            sessionManager.setCurrentSession(s);
            if (s == null) {
                log.warn("Web Services Login failed to establish session for id={} ip={}", id, ipAddress);
                throw new WebApplicationException("Unable to establish session", Response.Status.FORBIDDEN);
            } else {
                // We do not care too much on the off-chance that this fails - folks simply won't show up in presense
                // and events won't be trackable back to people / IP Addresses - but if it fails - there is nothing
                // we can do anyways.

                usageSessionService.login(user.getId(), id, ipAddress, "SakaiPortalLogin.jws", UsageSessionService.EVENT_LOGIN_WS);

                try {
                    String siteId = siteService.getUserSiteId(s.getUserId());
                    
                    if(userExists) {
                    	String current = StringUtils.join(user.getEmail(), user.getFirstName(), user.getLastName());
                    	String update = StringUtils.join(eMail, firstName, lastName);
                    	
                    	// If not equals, try to update user...
                    	if ( ! StringUtils.equals(current, update)) {
                    		try {
                    			log.error("Updating user with {}, {}, {} and {}", user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
                    			UserEdit edit = userDirectoryService.editUser(user.getId());
                    			edit.setEmail(eMail);
                    			edit.setFirstName(firstName);
                    			edit.setLastName(lastName);
                    			try {
                    				userDirectoryService.commitEdit(edit);
                    				log.info("User updated successfully");
                    			} catch (UserAlreadyDefinedException e) {
                    				log.error("Error committing user update transaction...", e);
                    				userDirectoryService.cancelEdit(edit);
                    			}
                    		} catch(UserNotDefinedException | UserPermissionException | UserLockedException e) {
                    			log.error("Error trying to update user...", e);
                    		}
                    	}
                    }
                    
                    log.info("Site exists... {}", siteId);
                } catch(Exception e) {
                    log.error("Site does not exist...");
                        throw new WebApplicationException("Failed login", Response.Status.FORBIDDEN);
                }
                if ( log.isDebugEnabled() ) log.debug("Sakai Portal Login id={} ip={} session={}", id, ipAddress, s.getId());
                    return s.getId();
            }
        }
        log.info("SakaiPortalLogin Failed ip={}", ipAddress);
        	throw new WebApplicationException("Failed login", Response.Status.FORBIDDEN);
    }

    /**
     * Login to an existing Sakai account with the Sakai user ID and shared portal secret.
     *
     * @param id Sakai user ID.
     * @param pw Shared portal secret.
     * @return Session ID of successful login.
     * @ if there are any problems logging in.
     */
    @WebMethod
    @Path("/login")
    @Produces("text/plain")
    @GET
    public String login(
    		@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @WebParam(name = "id", partName = "id") @QueryParam("id") String id){
        log.debug("SakaiPortalLogin.login()");
        return loginAndCreate(authorization, id, null, null, null);
    }
    
    private String validateUserAddRequest(String authorization) {
		if (StringUtils.isBlank(authorization)) {
			throw new WebApplicationException("Bad request...", Response.Status.BAD_REQUEST);
		}
		return validateRequest(authorization);
	}

	private String validateRequest(String authorization) {

		String ipAddress = getUserIp();
		String portalSecret = serverConfigurationService.getString("webservice.portalsecret");
		String portalIPs = serverConfigurationService.getString("webservice.portalIP");
		String ipCheck = serverConfigurationService.getString("webservice.IPCheck");

		// userDirectoryService. 7T17SMT2B04RP9T
		if (StringUtils.isAnyBlank(portalSecret, authorization) || !portalSecret.equals(authorization)) {
			log.info("SakaiPortalLogin secret mismatch ip=" + ipAddress);
			throw new WebApplicationException("Failed login", Response.Status.UNAUTHORIZED);
		}

		// Verify that this IP address matches our string
		if ("true".equalsIgnoreCase(ipCheck)) {
			if (StringUtils.isBlank(portalIPs) || portalIPs.indexOf(ipAddress) == -1) {
				log.info("SakaiPortalLogin Trusted IP not found");
				throw new WebApplicationException("Failed login", Response.Status.FORBIDDEN);
			}
		}
		return ipAddress;
	}
    
}
