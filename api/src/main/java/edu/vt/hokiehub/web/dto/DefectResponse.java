package edu.vt.hokiehub.web.dto;

import edu.vt.hokiehub.domain.ListingDefect;

public record DefectResponse(Long id, String description, String severity) {

    public static DefectResponse from(ListingDefect defect) {
        return new DefectResponse(defect.getId(), defect.getDescription(),
                defect.getSeverity().value());
    }
}
