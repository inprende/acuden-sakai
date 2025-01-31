<%@ page import="org.sakaiproject.umem.tool.ui.SiteListBean"%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f" %>
<%@ taglib uri="http://sakaiproject.org/jsf2/sakai" prefix="sakai" %>
<%@ taglib uri="http://myfaces.apache.org/tomahawk" prefix="t"%>

<%
	response.setContentType("text/html; charset=UTF-8");
	response.addDateHeader("Expires", System.currentTimeMillis() - (1000L * 60L * 60L * 24L * 365L));
	response.addDateHeader("Last-Modified", System.currentTimeMillis());
	response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
	response.addHeader("Pragma", "no-cache");
%>

<jsp:useBean id="msgs" class="org.sakaiproject.util.ResourceLoader" scope="session">
   <jsp:setProperty name="msgs" property="baseName" value="org.sakaiproject.umem.tool.bundle.Messages"/>
</jsp:useBean>

<f:view>
<sakai:view title="#{msgs.tool_title}">
	<script src="/library/js/spinner.js"></script>
	<script src="/sakai-usermembership-tool/usermembership/js/usermembership.js"></script>
	<link href="/sakai-usermembership-tool/usermembership/css/usermembership.css" rel="stylesheet" type="text/css" media="all"></link>

	<script>
		sakaiUserMembership.frameID = "<%= SiteListBean.getFrameID() %>";
	</script>

	<%/*<sakai:flowState bean="#{SiteListBean}"/>*/%>
	<h:outputText value="#{SiteListBean.initValues}"/>
	
	<f:subview id="allowed">
		<h:message for="allowed" fatalClass="alertMessage" fatalStyle="margin-top: 15px;" showDetail="true"/>
	</f:subview>

	<h:form id="sitelistform" rendered="#{SiteListBean.allowed}">
		<t:div styleClass="page-header">
			<h1><h:outputText value="#{msgs.title_sitelist} (#{SiteListBean.userDisplayId})"/></h1>
		</t:div>
		<sakai:instruction_message value="#{msgs.instructions_sitelist}" />

		<t:dataTable
			value="#{SiteListBean.userSitesRows}"
			var="row1"
			styleClass="table table-hover table-striped table-bordered"
			columnClasses="d-table-cell d-none d-sm-table-cell d-none d-md-table-cell d-none d-lg-table-cell d-table-cell"
			sortColumn="#{SiteListBean.sitesSortColumn}"
            sortAscending="#{SiteListBean.sitesSortAscending}"
            rendered="#{SiteListBean.renderTable}" >
			<h:column id="statusToggle">
				<f:facet name="header">
					<h:selectBooleanCheckbox title="#{msgs.select_all}/#{msgs.deselect_all}" styleClass="selectAllCheckbox" onclick="sakaiUserMembership.selectAll();" />
				</f:facet>
				<h:selectBooleanCheckbox value="#{row1.selected}" styleClass="chkStatus" />
			</h:column>
			<h:column id="siteName">
				<f:facet name="header">
		            <t:commandSortHeader columnName="siteName" immediate="true" arrow="true">
		                <h:outputText value="#{msgs.site_name}"/>
		            </t:commandSortHeader>
		        </f:facet>
				<h:outputLink target="_top" value="#{row1.siteURL}">
					<h:outputText value="#{row1.siteTitle}"/>
				</h:outputLink>
			</h:column>
			<h:column id="groups">
				<f:facet name="header">
		            <h:outputText value="#{msgs.groups}"/>
		        </f:facet>
				<h:outputText value="#{row1.groups}"/>
			</h:column>
			<t:column id="siteType" headerstyleClass="d-none d-sm-table-cell">
				<f:facet name="header">
		            <t:commandSortHeader columnName="siteType" immediate="true" arrow="true">
		                <h:outputText value="#{msgs.site_type}"/>
		            </t:commandSortHeader>
		        </f:facet>
				<h:outputText value="#{row1.siteType}"/>
			</t:column>
			<t:column id="siteTerm" headerstyleClass="d-none d-sm-table-cell">
				<f:facet name="header">
		            <t:commandSortHeader columnName="siteTerm" immediate="true" arrow="true">
		                <h:outputText value="#{msgs.site_term}"/>
		            </t:commandSortHeader>
		        </f:facet>
				<h:outputText value="#{row1.siteTerm}"/>
			</t:column>
			<t:column id="roleID" headerstyleClass="d-none d-sm-table-cell">
				<f:facet name="header">
		            <t:commandSortHeader columnName="roleId" immediate="true" arrow="true">
		                <h:outputText value="#{msgs.role_name}"/>
		            </t:commandSortHeader>
		        </f:facet>
				<h:outputText value="#{row1.roleName}"/>
			</t:column>
			<t:column id="pubView" headerstyleClass="d-none d-sm-table-cell">
				<f:facet name="header">
		            <t:commandSortHeader columnName="published" immediate="true" arrow="true">
		                <h:outputText value="#{msgs.status}"/>
		            </t:commandSortHeader>
		        </f:facet>
				<h:outputText value="#{row1.pubView}"/>
			</t:column>
			<h:column id="userStatus">
				<f:facet name="header">
		            <t:commandSortHeader columnName="userStatus" immediate="true" arrow="true">
		                <h:outputText value="#{msgs.site_user_status}"/>
		            </t:commandSortHeader>
		        </f:facet>
				<h:outputText value="#{row1.userStatus}"/>
			</h:column>
		</t:dataTable>

		<h:panelGroup rendered="#{SiteListBean.emptySiteList}">
			<p class="instruction" style="margin-top: 40px;">
				<h:outputText value="#{msgs.no_sitelist}" />
			</p>
		</h:panelGroup>

		<h:form id="buttonholder">
			<t:div rendered="#{SiteListBean.renderTable && !SiteListBean.emptySiteList}">
				<t:div styleClass="act">
					<h:commandButton id="invert-selection" type="button" title="#{msgs.invert_selection}" value="#{msgs.invert_selection}" onclick="sakaiUserMembership.invertSelection();" />
					<h:commandButton id="set-to-inactive" actionListener="#{SiteListBean.setToInactive}" value="#{msgs.set_to_inactive_button}"
									 onclick="SPNR.disableControlsAndSpin( this, null );" />
					<h:commandButton id="set-to-active" actionListener="#{SiteListBean.setToActive}" value="#{msgs.set_to_active_button}"
									 onclick="SPNR.disableControlsAndSpin( this, null );" />
					<h:commandButton id="export-csv" actionListener="#{SiteListBean.exportAsCsv}" value="#{msgs.export_selected_to_csv}" />
					<h:commandButton id="export-xls" actionListener="#{SiteListBean.exportAsXls}" value="#{msgs.export_selected_to_excel}" />
				</t:div>
			</t:div>

			<t:div styleClass="act">
				<h:commandButton id="userlist" action="#{SiteListBean.processActionBack}" value="#{msgs.back_button}" styleClass="active" onclick="SPNR.disableControlsAndSpin( this, null );" />
			</t:div>
		</h:form>
	</h:form>
</sakai:view>
</f:view>
