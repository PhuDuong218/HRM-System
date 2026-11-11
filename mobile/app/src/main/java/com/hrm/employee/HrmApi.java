package com.hrm.employee;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class HrmApi {
    private final String baseUrl;

    public HrmApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static final class ApiException extends Exception {
        public final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public JSONObject login(String username, String password) throws Exception {
        return request("/api/auth/login", "POST", null,
                new JSONObject().put("username", username).put("password", password));
    }

    public JSONObject me(String token) throws Exception {
        return request("/api/auth/me", "GET", token, null);
    }

    public void logout(String token) throws Exception {
        request("/api/auth/logout", "POST", token, null);
    }

    public JSONObject get(String path,String token)throws Exception{return request(path,"GET",token,null);}
    public JSONObject send(String path,String method,String token,JSONObject body)throws Exception{return request(path,method,token,body);}
    private JSONObject request(String path, String method, String token, JSONObject body) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty())
            throw new ApiException(0, "Chưa cấu hình địa chỉ máy chủ cho bản ứng dụng này.");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null)
                connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(data);
                }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream()
                    : connection.getErrorStream();
            String text = "";
            if (input != null)
                try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > 1048576)
                            throw new IOException("Unexpected response size");
                        out.write(buffer, 0, n);
                    }
                    text = out.toString(StandardCharsets.UTF_8.name());
                }
            if (status < 200 || status >= 300) {
                String message = status == 401 ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                        : status == 403 ? "Tài khoản không có quyền thực hiện thao tác."
                                : status >= 500 ? "Máy chủ đang gặp lỗi. Vui lòng thử lại."
                                        : "Không thực hiện được yêu cầu (HTTP " + status + ").";
                if(status==400||status==409){try{JSONObject problem=new JSONObject(text);String detail=problem.optString("loi_chi_tiet",problem.optString("detail",problem.optString("message","")));if(!detail.isEmpty()&&detail.length()<1000)message=detail;}catch(JSONException ignored){}}throw new ApiException(status, message);
            }
            return text.trim().isEmpty() ? new JSONObject() : text.trim().startsWith("[")?new JSONObject().put("items",new org.json.JSONArray(text)):new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    public JSONArray getArray(String path, String token) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty())
            throw new ApiException(0, "Chưa cấu hình địa chỉ máy chủ.");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) return new JSONArray();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return new JSONArray(sb.toString());
        }
    }

    public static String message(Exception error) {
        if (error instanceof ApiException)
            return error.getMessage();
        if (error instanceof SocketTimeoutException)
            return "Máy chủ phản hồi quá lâu. Vui lòng thử lại.";
        if (error instanceof IOException)
            return "Không kết nối được máy chủ HRM. Kiểm tra backend cổng 2005 và kết nối mạng.";
        if (error instanceof JSONException)
            return "Phản hồi máy chủ không hợp lệ. Vui lòng thử lại.";
        return "Không thể thực hiện thao tác. Vui lòng thử lại.";
    }
}
