package com.frameworkium.core.common.reporting.jira.util;

import com.frameworkium.core.common.properties.Property;
import com.frameworkium.core.common.reporting.jira.dto.executionsearch.ExecutionDto;
import com.frameworkium.core.common.reporting.jira.dto.executionsearch.ExecutionSearchDto;
import com.frameworkium.core.common.reporting.jira.service.ExecutionSearch;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExecutionSearchUtil {
    private final ExecutionSearchDto result;

    public ExecutionSearchUtil(String query) {
        final ExecutionSearch executionSearch = new ExecutionSearch();
        this.result = executionSearch.search(query);
    }

    /**
     * Get a list of execution Ids optionally filtered by Property.ZAPI_CYCLE_REGEX
     *
     * @return a list of execution Ids
     */
    public List<Integer> getExecutionIdsByZAPICycleRegex() {
        return getExecutionStream()
                .map(e -> e.id.intValue())
                .collect(Collectors.toList());
    }

    /**
     * Get a list of execution status Ids optionally filtered by Property.ZAPI_CYCLE_REGEX
     *
     * @return a list of execution status Ids
     */
    public List<Integer> getExecutionStatusesByZAPICycleRegex() {
        return getExecutionStream()
                .map(e -> e.status.id.intValue())
                .collect(Collectors.toList());
    }

    private Stream<ExecutionDto> getExecutionStream() {
        if (Property.ZAPI_CYCLE_REGEX.isSpecified()) {
            return result.executions.stream()
                    .filter(e -> e.cycleName.equals(Property.ZAPI_CYCLE_REGEX.getValue()));
        }
        return result.executions.stream();
    }
}
