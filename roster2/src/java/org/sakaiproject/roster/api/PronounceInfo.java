package org.sakaiproject.roster.api;

public class PronounceInfo {
    public String recordingUrl;
    public String pronouns;
    public String embedCode;

    public PronounceInfo(String recordingUrl, String pronouns, String embedCode) {
        this.recordingUrl = recordingUrl;
        this.pronouns = pronouns;
        this.embedCode = embedCode;
    }
}

