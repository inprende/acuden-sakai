package org.sakaiproject.site.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CourseGrade implements Serializable{

	private static final long serialVersionUID = -7131462011942864032L;

	private SiteDTO course;
	private Long assessmentId;
    private String assessmentName;
	private String grade;
	private Long submitDate;
    private Double percent;
    private Double correct;
    private Double total;
    private boolean gradeAvailable;
}
