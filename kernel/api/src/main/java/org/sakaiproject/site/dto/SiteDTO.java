package org.sakaiproject.site.dto;

import java.util.Date;

import javax.xml.bind.annotation.XmlRootElement;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@XmlRootElement(name = "SiteDTO")
public class SiteDTO {

	private String id;
	private String url;
	private String description;
	private String infoUrl;
	private String infoUrlFull;
	private String shortDescription;
	private String title;
	private String type;
	boolean joinable;
	boolean published;
	boolean pubView;
	private Date createdDate;
	private Date modifiedDate;
	
}
