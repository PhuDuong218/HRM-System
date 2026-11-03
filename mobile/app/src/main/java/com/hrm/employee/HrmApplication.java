package com.hrm.employee;

import android.app.Application;
import org.json.JSONObject;

/**
 * Session stays in app memory only; process death requires signing in again.
 */
public class HrmApplication extends Application {
    private String token;
    private JSONObject profile;

    public String token() {
        return token;
    }

    public JSONObject profile() {
        return profile;
    }

    public void signIn(String token, JSONObject profile) {
        this.token = token;
        this.profile = profile;
    }

    public void clearSession() {
        token = null;
        profile = null;
    }
}
