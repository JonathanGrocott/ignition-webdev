package com.jg.ignition.webcompat.gateway;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import com.inductiveautomation.ignition.common.tags.config.model.TagReferenceLocation;
import com.jg.ignition.webcompat.common.WebCompatModule;

public class WebDevTagReferenceLocation extends TagReferenceLocation {
    public WebDevTagReferenceLocation(String project, String path, String method) {
        super(TagReferenceLocation.builder(new ResourceId(project, WebCompatModule.RESOURCE_TYPE, path))
                .set("script", method));
    }
}
