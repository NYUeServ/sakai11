package edu.nyu.classes.nyugrades.api;

import java.util.Map;
import java.util.Collection;


public interface NYUGradesService
{
    public String findSingleSection(String courseId,
                                    String strm,
                                    String sessionCode,
                                    String classSection)
        throws SectionNotFoundException, MultipleSectionsMatchedException;

    public GradeSet getGradesForSection(String sectionEid)
        throws SiteNotFoundForSectionException, MultipleSitesFoundForSectionException, GradePullDisabledException, AuditLogException, AmbiguousGradeMappingException;
}
