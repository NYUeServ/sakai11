package org.sakaiproject.component.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sakaiproject.component.cover.HotReloadConfigurationService;

public class NYUConfigurationService extends BasicConfigurationService {

    // Any properties listed here will be fetched from
    // HotReloadConfigurationService.
    //
    // If you add a new property, you'll need to be sure that the code that uses
    // it can actually deal with the value changing at runtime.
    //
    private String hotReloadProperties = null;
    private List<String> hotReloadPropertiesList = Collections.<String>emptyList();

    public String getString(String name, String defaultValue) {
        if (HotReloadConfigurationService.getHotReloadOverrideProperties().indexOf(name) >= 0) {
            return HotReloadConfigurationService.getString(name, defaultValue);
        }

        return super.getString(name, defaultValue);
    }

}
