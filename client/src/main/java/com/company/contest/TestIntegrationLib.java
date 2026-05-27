package com.company.contest;

public class TestIntegrationLib {
    public Boolean publicField;
    private Boolean privateField;

    public TestIntegrationLib() {

    }

    public void changePrivate(boolean value) {
        privateField = value;
    }

    public void changePublic(boolean value) {
        publicField = value;
    }
}
