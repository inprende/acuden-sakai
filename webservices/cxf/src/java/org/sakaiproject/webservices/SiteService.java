package org.sakaiproject.webservices;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.portal.api.PortalService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.site.dto.CourseGrade;
import org.sakaiproject.site.dto.SiteDTO;
import org.sakaiproject.site.dto.SiteUserRequest;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.assessment.data.dao.assessment.PublishedAssessmentData;
import org.sakaiproject.tool.assessment.data.dao.assessment.PublishedSectionData;
import org.sakaiproject.tool.assessment.data.dao.grading.AssessmentGradingData;
import org.sakaiproject.tool.assessment.data.ifc.assessment.AssessmentTemplateIfc;
import org.sakaiproject.tool.assessment.services.PersistenceService;
import org.sakaiproject.tool.assessment.services.assessment.PublishedAssessmentService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.util.api.FormattedText;

import lombok.extern.slf4j.Slf4j;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
@Slf4j
public class SiteService extends AbstractWebService {
	
	private final PortalService portalService = (PortalService) ComponentManager.get(PortalService.class);
	
	private final String SITE_MATCH = "ACUDEN Academy LMS";
	private final String MODULE_MATCH = "Módulo ";

	private final ModelMapper modelMapper = new ModelMapper();
	private final PublishedAssessmentService assessmentService = new PublishedAssessmentService();

	private User getSakaiUser(String id) {
		User user = null;

		try {
			user = userDirectoryService.getUserByEid(id);
		} catch (Exception e) {
			user = null;
		}
		return user;
	}

	@Path("/siteModules")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public List<SiteDTO> getSites(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {

		String ipAddress = validateRequest(authorization);
		log.info("Client ip: {}", ipAddress);

		try {
			List<SiteDTO> responseSitesModules = new ArrayList<>();
			List<Site> pagedSites = siteService.getSites(SelectionType.JOINABLE, "course", SITE_MATCH, null, SortType.ID_ASC, null);
			pagedSites.stream().filter(Site::isPublished).forEach(site -> {
				site.getPages().stream().filter(sp -> sp.getTitle().contains(MODULE_MATCH)).forEach(module -> {
					SiteDTO dto = toSiteDTO(site);
					dto.setModuleId(module.getId());
					dto.setModuleTitle(module.getTitle());
					dto.setModuleUrl(module.getUrl());
					dto.setModuleOrder(module.getPosition());
					responseSitesModules.add(dto);
				});
			});
			return responseSitesModules;
		} catch (Exception e) {
			log.error("Error searching for sites...");
			throw new WebApplicationException("Failed processing sites", Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@Path("/siteModule")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public SiteDTO getSite(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
		String ipAddress = validateRequest(authorization);
		log.info("Client ip: {}", ipAddress);

		try {
			return toSiteDTO(siteService.getSite(siteId));
		} catch (IdUnusedException e) {
			log.error("Error searching for sites...");
			throw new WebApplicationException("Failed processing sites", Response.Status.NOT_FOUND);
		}
	}

	@Path("/addUser")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public SiteDTO addUser(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, SiteUserRequest request) throws IdUnusedException, PermissionException {

		String ipAddress = validateUserAddRequest(authorization, request);
		log.info("Client ip: {}", ipAddress);

		User user = getSakaiUser(request.getUserEid());
		if (user == null || ! StringUtils.equals(user.getEid(), request.getUserEid())) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}

		Site site = null;

		try {
			site = siteService.getSite(request.getSiteId());
		} catch (IdUnusedException e) {
			throw new WebApplicationException("Invalid request", e, Response.Status.NOT_FOUND);
		}

		if (site == null || ! request.getSiteId().equals(site.getId())) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}

		try {
			Session s = sessionManager.startSession();
			sessionManager.setCurrentSession(s);
			if (s == null) {
				log.warn("Web Services Login failed to establish session for id={} ip={}", request.getUserEid(), ipAddress);
				throw new WebApplicationException("Unable to establish session", Response.Status.FORBIDDEN);
			} else {
				usageSessionService.login(user.getId(), request.getUserEid(), ipAddress, "SiteService.jws", UsageSessionService.EVENT_LOGIN_WS);
				siteService.join(site.getId());
				try {
					List<String> pinnedSites = portalService.getPinnedSites();
					if(CollectionUtils.isEmpty(pinnedSites.stream().filter(ps -> request.getSiteId().equals(ps)).collect(Collectors.toList()))) {
						portalService.addPinnedSite(user.getId(), request.getSiteId());
					}
				} catch (Exception e) {
					log.warn("Error while trying to pin site for the user");
				}
			}
		}catch (Exception e) {
			log.error("Error adding user into site.");
		}
		return toSiteDTO(site);
	}
	
	@Path("/siteModuleGrades")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public List<CourseGrade> getCourseGrade(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
			@WebParam(name = "id", partName = "id") @QueryParam("id") String id,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
		
		SiteUserRequest request = new SiteUserRequest(id, siteId);

		String ipAddress = validateUserAddRequest(authorization, request);
		log.info("Client ip: {}", ipAddress);

		User user = getSakaiUser(id);
		if (user == null || !StringUtils.equals(user.getEid(), request.getUserEid())) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}
		Site site = null;
		try {
			site = siteService.getSite(request.getSiteId());
		} catch (IdUnusedException e) {
			throw new WebApplicationException("Invalid request", e, Response.Status.NOT_FOUND);
		}

		if (site == null) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}


		List<AssessmentGradingData> list = assessmentService.getAllAssessmentsGradingDataByAgentAndSiteId(user.getId(), site.getId())
				.stream()
				.filter(agd -> agd.getStatus() == AssessmentTemplateIfc.ACTIVE_STATUS)
				.collect(Collectors.toList());
		
		List<CourseGrade> grades = getCourseGrades(list, site);
		
		return grades;
	}
	
	@Path("/siteModuleGrade")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public CourseGrade getCourseGrade(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
			@WebParam(name = "id", partName = "id") @QueryParam("id") String id,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "moduleId", partName = "moduleId") @QueryParam("moduleId") String moduleId) {
		
		SiteUserRequest request = new SiteUserRequest(id, siteId);

		String ipAddress = validateUserAddRequest(authorization, request);
		log.info("Client ip: {}", ipAddress);

		User user = getSakaiUser(id);
		if (user == null || !StringUtils.equals(user.getEid(), request.getUserEid())) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}
		Site site = null;
		try {
			site = siteService.getSite(request.getSiteId());
		} catch (IdUnusedException e) {
			throw new WebApplicationException("Not Found", e, Response.Status.NOT_FOUND);
		}

		if (site == null) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}
		
		CourseGrade grade = getInternalCourseGrade(site, user, moduleId);
		
		return grade;
	}
	
	@Path("/process")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public void processGrades(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, SiteUserRequest request) {

		String ipAddress = validateUserAddRequest(authorization, request);
		
		log.info("Client ip: {}", ipAddress);

		User user = getSakaiUser(request.getUserEid());
		if (user == null || ! StringUtils.equals(user.getEid(), request.getUserEid())) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}
		Site site = null;
		try {
			site = siteService.getSite(request.getSiteId());
		} catch (IdUnusedException e) {
			throw new WebApplicationException("Invalid request", e, Response.Status.NOT_FOUND);
		}

		if (site == null) {
			throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
		}
		
		return;
	}

	private String validateUserAddRequest(String authorization, SiteUserRequest request) {
		if (request == null || StringUtils.isAnyBlank(authorization, request.getSiteId(), request.getUserEid())) {
			throw new WebApplicationException("Bad request...", Response.Status.BAD_REQUEST);
		}
		return validateRequest(authorization);
	}

	private String validateRequest(String authorization) {

		String ipAddress = getUserIp();
		String portalSecret = serverConfigurationService.getString("webservice.portalsecret");
		String portalIPs = serverConfigurationService.getString("webservice.portalIP");
		String ipCheck = serverConfigurationService.getString("webservice.IPCheck");

		if (StringUtils.isAnyBlank(portalSecret, authorization) || !portalSecret.equals(authorization)) {
			log.info("Sakai secret mismatch ip=" + ipAddress);
			throw new WebApplicationException("Failed login", Response.Status.UNAUTHORIZED);
		}

		// Verify that this IP address matches our string
		if ("true".equalsIgnoreCase(ipCheck)) {
			if (portalIPs == null || portalIPs.equals("") || portalIPs.indexOf(ipAddress) == -1) {
				log.info("Sakai Trusted IP not found");
				throw new WebApplicationException("Failed login", Response.Status.FORBIDDEN);
			}
		}
		return ipAddress;
	}
	
	private SiteDTO toSiteDTO(Site site) {
		return modelMapper.map(site, SiteDTO.class);
	}
	
	private List<CourseGrade> getCourseGrades(List<AssessmentGradingData> list, Site site) {
		List<CourseGrade> dataList = new ArrayList<>();
		for (AssessmentGradingData agd : list) {

			Long publishAssessmentId = agd.getPublishedAssessmentId();
			PublishedAssessmentData publishedAssessmentData = assessmentService
					.getBasicInfoOfPublishedAssessment(publishAssessmentId.toString());
			String title = ComponentManager.get(FormattedText.class)
					.convertFormattedTextToPlaintext(publishedAssessmentData.getTitle());
			Date submitDate = agd.getSubmittedDate();
			Double finalScore = agd.getFinalScore();
			//Long assessmentGradingId = agd.getAssessmentGradingId();

			PublishedAssessmentData assessmentData = PersistenceService.getInstance().getPublishedAssessmentFacadeQueries().loadPublishedAssessment(publishAssessmentId);
			Set<PublishedSectionData> sectionSet = PersistenceService.getInstance().getPublishedAssessmentFacadeQueries().getSectionSetForAssessment(assessmentData);
			assessmentData.setSectionSet(sectionSet);

			Double maxScore = assessmentData.getTotalScore();
			Double percentage = 0.0;
			boolean availableGrade = true;
			if (maxScore != null && maxScore != 0) {
				BigDecimal finalScoreBigDecimal = new BigDecimal(finalScore.toString());
				BigDecimal maxScoreBigDecimal = new BigDecimal(maxScore.toString());
				BigDecimal grade_temp = (finalScoreBigDecimal.divide(maxScoreBigDecimal, new MathContext(10))).multiply(new BigDecimal(100));
				percentage = grade_temp.doubleValue();
			} else {
				availableGrade = false;
			}
			CourseGrade sad = new CourseGrade();
			sad.setAssessmentId(publishAssessmentId);
			sad.setAssessmentName(title);
			sad.setSubmitDate(submitDate == null ? 0L : submitDate.getTime());
			sad.setPercent(percentage);
			sad.setCorrect(finalScore);
			sad.setTotal(maxScore);
			sad.setGradeAvailable(availableGrade);
			sad.setCourse(toSiteDTO(site));
			dataList.add(sad);
		}
		return dataList;
	}
	
	private CourseGrade getInternalCourseGrade(Site site, User user, String moduleId) {
		
		
		List<AssessmentGradingData> list = assessmentService.getAllAssessmentsGradingDataByAgentAndSiteId(user.getId(), site.getId())
				.stream()
				.filter(agd -> agd.getStatus() == AssessmentTemplateIfc.ACTIVE_STATUS)
				.collect(Collectors.toList());
		
		if (list.size() < 1) {
			throw new WebApplicationException("Not Found", Response.Status.NOT_FOUND);
		}
		
		SitePage page = site.getPage(moduleId);
		
		//null vars
		AssessmentGradingData data = null;
		Long publishAssessmentId = null;
		
		String title = null;
		
		for (AssessmentGradingData agd : list) {
			Long id = agd.getPublishedAssessmentId();
			PublishedAssessmentData pad = assessmentService.getBasicInfoOfPublishedAssessment(id.toString());
			String t = ComponentManager.get(FormattedText.class).convertFormattedTextToPlaintext(pad.getTitle());
			if(StringUtils.contains(t, page.getTitle())) {
				data = agd;
				publishAssessmentId = id;
				title = t;
				break;
			}
		}

		Date submitDate = data.getSubmittedDate();
		Double finalScore = data.getFinalScore();
		PublishedAssessmentData assessmentData = PersistenceService.getInstance().getPublishedAssessmentFacadeQueries().loadPublishedAssessment(publishAssessmentId);
		
		Double maxScore = assessmentData.getTotalScore();
		Double percentage = 0.0;
		boolean availableGrade = true;
		if (maxScore != null && maxScore != 0) {
			BigDecimal finalScoreBigDecimal = new BigDecimal(finalScore.toString());
			BigDecimal maxScoreBigDecimal = new BigDecimal(maxScore.toString());
			BigDecimal grade_temp = (finalScoreBigDecimal.divide(maxScoreBigDecimal, new MathContext(10))).multiply(new BigDecimal(100));
			percentage = grade_temp.doubleValue();
		} else {
			availableGrade = false;
		}
		
		CourseGrade sad = new CourseGrade();
		sad.setAssessmentId(publishAssessmentId);
		sad.setAssessmentName(title);
		sad.setSubmitDate(submitDate == null ? 0L : submitDate.getTime());
		sad.setPercent(percentage);
		sad.setCorrect(finalScore);
		sad.setTotal(maxScore);
		sad.setGradeAvailable(availableGrade);
		sad.setCourse(toSiteDTO(site));
		
		return sad;
	}
}
