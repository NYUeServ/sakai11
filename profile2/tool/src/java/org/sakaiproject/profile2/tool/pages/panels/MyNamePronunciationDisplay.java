package org.sakaiproject.profile2.tool.pages.panels;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.profile2.logic.ProfileLogic;
import org.sakaiproject.profile2.logic.SakaiProxy;
import org.sakaiproject.profile2.model.UserProfile;
import org.sakaiproject.profile2.util.ProfileUtils;

@Slf4j
public class MyNamePronunciationDisplay extends Panel {

    @SpringBean(name="org.sakaiproject.profile2.logic.SakaiProxy")
    private SakaiProxy sakaiProxy;

    @SpringBean(name="org.sakaiproject.profile2.logic.ProfileLogic")
    private ProfileLogic profileLogic;

    private int visibleFieldCount = 0;
    private final UserProfile userProfile;

    public MyNamePronunciationDisplay(final String id, final UserProfile userProfile) {
        super(id);

        log.debug("MyNamePronunciationDisplay()");

        final Component thisPanel = this;
        this.userProfile = userProfile;

        //heading
        add(new Label("heading", new ResourceModel("heading.name.pronunciation")));

        addPhoneticPronunciation();
        addNameRecord();

        AjaxFallbackLink editButton = new AjaxFallbackLink("editButton", new ResourceModel("button.edit")) {
            public void onClick(AjaxRequestTarget target) {
                Component newPanel = new MyNamePronunciationEdit(id, userProfile);
                newPanel.setOutputMarkupId(true);
                thisPanel.replaceWith(newPanel);
                if(target != null) {
                    target.add(newPanel);
                    target.appendJavaScript("setMainFrameHeight(window.name);");
                }
            }
        };
        editButton.add(new Label("editButtonLabel", new ResourceModel("button.edit")));
        editButton.setOutputMarkupId(true);
        if(userProfile.isLocked() && !sakaiProxy.isSuperUser()) {
            editButton.setVisible(false);
        }
        add(editButton);

        //no fields message
        Label noFieldsMessage = new Label("noFieldsMessage", new ResourceModel("text.no.fields"));
        add(noFieldsMessage);
        if(visibleFieldCount > 0) {
            noFieldsMessage.setVisible(false);
        }
    }

    private void addPhoneticPronunciation() {
        WebMarkupContainer phoneticPronunciationContainer = new WebMarkupContainer("phoneticPronunciationContainer");

        phoneticPronunciationContainer.add(new Label("phoneticPronunciationLabel", new ResourceModel("profile.phonetic")));
        phoneticPronunciationContainer.add(new Label("phoneticPronunciation", ProfileUtils.processHtml(userProfile.getPhoneticPronunciation())).setEscapeModelStrings(false));
        add(phoneticPronunciationContainer);

        if (StringUtils.isBlank(userProfile.getPhoneticPronunciation())) phoneticPronunciationContainer.setVisible(false);
        else visibleFieldCount++;
    }

    private void addNameRecord() {
        WebMarkupContainer nameRecordingContainer = new WebMarkupContainer("nameRecordingContainer");

        nameRecordingContainer.add(new Label("nameRecordingLabel", new ResourceModel("profile.name.recording")));
        WebMarkupContainer audioPlayer = new WebMarkupContainer("audioPlayer");

        final String slash = Entity.SEPARATOR;
        final StringBuilder path = new StringBuilder();
        path.append(slash);
        path.append("direct");
        path.append(slash);
        path.append("profile");
        path.append(slash);
        path.append(userProfile.getUserUuid());
        path.append(slash);
        path.append("pronunciation");
        path.append("?v=");
        path.append(RandomStringUtils.random(8, true, true));

        audioPlayer.add(new AttributeModifier("src", path.toString()));
        nameRecordingContainer.add(audioPlayer);
        add(nameRecordingContainer);

        if (profileLogic.getUserNamePronunciation(userProfile.getUserUuid()) == null) nameRecordingContainer.setVisible(false);
        else visibleFieldCount++;
    }
}
