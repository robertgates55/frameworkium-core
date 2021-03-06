package com.frameworkium.core.common.reporting.jira.service;

import static org.apache.http.HttpStatus.SC_OK;

import com.frameworkium.core.common.reporting.jira.dto.project.ProjectDto;
import com.frameworkium.core.common.reporting.jira.dto.version.VersionDto;
import com.frameworkium.core.common.reporting.jira.endpoint.JiraEndpoint;
import io.restassured.http.ContentType;
import java.util.List;

public class Project extends AbstractJiraService {
  public ProjectDto getProject(String projectIdOrKey) {
    return getRequestSpec()
        .basePath(JiraEndpoint.PROJECT.getUrl())
        .pathParam("projectIdOrKey", projectIdOrKey)
        .contentType(ContentType.JSON)
        .get("/{projectIdOrKey}")
        .then()
        .log().ifValidationFails()
        .statusCode(SC_OK)
        .extract()
        .as(ProjectDto.class);
  }

  public List<VersionDto> getProjectVersions(String projectIdOrKey) {
    return getRequestSpec()
        .basePath(JiraEndpoint.PROJECT.getUrl())
        .pathParam("projectIdOrKey", projectIdOrKey)
        .get("/{projectIdOrKey}/versions")
        .then()
        .log().ifValidationFails()
        .statusCode(SC_OK)
        .extract()
        .body().jsonPath()
        .getList("", VersionDto.class);
  }
}
