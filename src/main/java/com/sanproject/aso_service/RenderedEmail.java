package com.sanproject.aso_service;

// Subject and bodies returned by the Clojure email-renderer service.
public class RenderedEmail {

    private String subject;
    private String textBody;
    private String htmlBody;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }
}
