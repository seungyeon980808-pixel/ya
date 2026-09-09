package com.malhaedwo.pttprobe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GoogleOAuth {
    public static final String CLIENT_ID = "YOUR_GOOGLE_OAUTH_CLIENT_ID.apps.googleusercontent.com";
    public static final String ALLOWED_EMAIL = "owner@example.invalid";
    public static final String REDIRECT_SCHEME = "com.googleusercontent.apps.YOUR_GOOGLE_OAUTH_CLIENT_ID";
    public static final String REDIRECT_URI = REDIRECT_SCHEME + ":/oauth2redirect";
    private static final String SCOPE = "openid email https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file";
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ACCESS_EXPIRY = "access_expiry";
    private static final String ACCOUNT_EMAIL = "account_email";
    private static final String PENDING_STATE = "pending_state";
    private static final String PENDING_VERIFIER = "pending_verifier";
    private static final Object TOKEN_LOCK = new Object();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback { void onComplete(boolean success, String message); }

    private GoogleOAuth() {}

    public static void begin(Activity activity, Callback callback) {
        try {
            String state = randomBase64Url(32);
            String verifier = randomBase64Url(64);
            String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            SecureTokenStore store = new SecureTokenStore(activity);
            store.put(PENDING_STATE, state);
            store.put(PENDING_VERIFIER, verifier);
            Uri uri = Uri.parse(AUTH_ENDPOINT).buildUpon()
                    .appendQueryParameter("client_id", CLIENT_ID)
                    .appendQueryParameter("redirect_uri", REDIRECT_URI)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("scope", SCOPE)
                    .appendQueryParameter("state", state)
                    .appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("access_type", "offline")
                    .appendQueryParameter("prompt", "consent")
                    .appendQueryParameter("include_granted_scopes", "true")
                    .appendQueryParameter("login_hint", ALLOWED_EMAIL)
                    .build();
            Intent browser = new Intent(Intent.ACTION_VIEW, uri);
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            activity.startActivity(browser);
            deliver(callback, true, "브라우저에서 Google 연결을 승인해주세요");
        } catch (Throwable error) {
            deliver(callback, false, "Google 연결 시작 실패: " + safe(error));
        }
    }

    public static boolean consumeCallback(Activity activity, Intent intent, Callback callback) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !REDIRECT_SCHEME.equals(uri.getScheme()) || !"/oauth2redirect".equals(uri.getPath())) return false;
        String returnedState = uri.getQueryParameter("state");
        String code = uri.getQueryParameter("code");
        String oauthError = uri.getQueryParameter("error");
        SecureTokenStore store = new SecureTokenStore(activity);
        String expectedState = store.get(PENDING_STATE);
        String verifier = store.get(PENDING_VERIFIER);
        if (expectedState == null || !constantTimeEquals(expectedState, returnedState)) {
            deliver(callback, false, "Google 연결 응답을 확인할 수 없습니다. 다시 연결해주세요");
            return true;
        }
        store.remove(PENDING_STATE);
        store.remove(PENDING_VERIFIER);
        if (oauthError != null || code == null || verifier == null) {
            deliver(callback, false, "Google 연결이 취소되었거나 거부되었습니다" + (oauthError == null ? "" : ": " + oauthError));
            return true;
        }
        Context app = activity.getApplicationContext();
        EXECUTOR.execute(() -> exchangeCode(app, code, verifier, callback));
        return true;
    }

    public static boolean isConnected(Context context) {
        SecureTokenStore store = new SecureTokenStore(context);
        return notEmpty(store.get(REFRESH_TOKEN)) && ALLOWED_EMAIL.equalsIgnoreCase(store.get(ACCOUNT_EMAIL));
    }

    public static String accountEmail(Context context) {
        String email = new SecureTokenStore(context).get(ACCOUNT_EMAIL);
        return email == null ? "" : email;
    }

    public static String getAccessToken(Context context) throws Exception {
        synchronized (TOKEN_LOCK) {
            SecureTokenStore store = new SecureTokenStore(context);
            if (!ALLOWED_EMAIL.equalsIgnoreCase(store.get(ACCOUNT_EMAIL))) throw new Exception("허용된 Google 계정이 연결되지 않았습니다");
            String cached = store.get(ACCESS_TOKEN);
            long expiry = parseLong(store.get(ACCESS_EXPIRY));
            if (notEmpty(cached) && expiry > System.currentTimeMillis() + 90_000L) return cached;
            String refresh = store.get(REFRESH_TOKEN);
            if (!notEmpty(refresh)) throw new Exception("Google 연결이 필요합니다");
            LinkedHashMap<String,String> fields = new LinkedHashMap<>();
            fields.put("client_id", CLIENT_ID);
            fields.put("refresh_token", refresh);
            fields.put("grant_type", "refresh_token");
            HttpResponse response = postForm(TOKEN_ENDPOINT, fields);
            if (response.code < 200 || response.code >= 300) {
                if (response.body.contains("invalid_grant")) store.clear();
                throw new Exception("Google 연결 갱신 실패 (HTTP " + response.code + "): " + responseError(response.body));
            }
            JSONObject json = new JSONObject(response.body);
            String access = json.optString("access_token", "");
            if (access.isEmpty()) throw new Exception("Google 액세스 토큰을 받지 못했습니다");
            long expiresIn = Math.max(300L, json.optLong("expires_in", 3600L));
            store.put(ACCESS_TOKEN, access);
            store.put(ACCESS_EXPIRY, String.valueOf(System.currentTimeMillis() + expiresIn * 1000L));
            return access;
        }
    }

    public static void clearCachedAccessToken(Context context) {
        SecureTokenStore store = new SecureTokenStore(context);
        store.remove(ACCESS_TOKEN);
        store.remove(ACCESS_EXPIRY);
    }

    public static void disconnect(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        SecureTokenStore store = new SecureTokenStore(app);
        String token = store.get(REFRESH_TOKEN);
        if (!notEmpty(token)) {
            store.clear();
            deliver(callback, true, "Google 동기화 연결을 해제했습니다");
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                revoke(token);
                synchronized (TOKEN_LOCK) {
                    SecureTokenStore current = new SecureTokenStore(app);
                    if (constantTimeEquals(token, current.get(REFRESH_TOKEN))) current.clear();
                }
                deliver(callback, true, "Google 권한과 동기화 연결을 해제했습니다");
            } catch (Throwable error) {
                deliver(callback, false, "Google 권한 해제에 실패해 연결 정보를 유지했습니다. 다시 시도해주세요: " + safe(error));
            }
        });
    }

    private static void exchangeCode(Context context, String code, String verifier, Callback callback) {
        try {
            LinkedHashMap<String,String> fields = new LinkedHashMap<>();
            fields.put("client_id", CLIENT_ID);
            fields.put("code", code);
            fields.put("code_verifier", verifier);
            fields.put("redirect_uri", REDIRECT_URI);
            fields.put("grant_type", "authorization_code");
            HttpResponse response = postForm(TOKEN_ENDPOINT, fields);
            if (response.code < 200 || response.code >= 300) {
                throw new Exception("토큰 교환 실패 (HTTP " + response.code + "): " + responseError(response.body));
            }
            JSONObject json = new JSONObject(response.body);
            String access = json.optString("access_token", "");
            String refresh = json.optString("refresh_token", "");
            if (access.isEmpty()) throw new Exception("Google 액세스 토큰을 받지 못했습니다");
            HttpResponse profile = authorizedGet(USERINFO_ENDPOINT, access);
            if (profile.code < 200 || profile.code >= 300) throw new Exception("Google 계정 확인 실패 (HTTP " + profile.code + ")");
            JSONObject user = new JSONObject(profile.body);
            String email = user.optString("email", "");
            boolean verified = user.optBoolean("email_verified", false);
            if (!verified || !ALLOWED_EMAIL.equalsIgnoreCase(email)) {
                try { revoke(access); } catch (Throwable ignored) {}
                throw new Exception("허용된 계정 " + ALLOWED_EMAIL + "만 연결할 수 있습니다");
            }
            SecureTokenStore store = new SecureTokenStore(context);
            if (refresh.isEmpty()) refresh = store.get(REFRESH_TOKEN);
            if (!notEmpty(refresh)) throw new Exception("오프라인 동기화용 연결 권한을 받지 못했습니다. 다시 동의해주세요");
            long expiresIn = Math.max(300L, json.optLong("expires_in", 3600L));
            store.put(REFRESH_TOKEN, refresh);
            store.put(ACCESS_TOKEN, access);
            store.put(ACCESS_EXPIRY, String.valueOf(System.currentTimeMillis() + expiresIn * 1000L));
            store.put(ACCOUNT_EMAIL, email);
            deliver(callback, true, email + " 계정 연결 완료");
        } catch (Throwable error) {
            deliver(callback, false, "Google 연결 실패: " + safe(error));
        }
    }

    private static HttpResponse authorizedGet(String endpoint, String accessToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(20_000);
        int code = connection.getResponseCode();
        String body = read(connection, code);
        connection.disconnect();
        return new HttpResponse(code, body);
    }

    private static HttpResponse postForm(String endpoint, Map<String,String> fields) throws Exception {
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String,String> entry : fields.entrySet()) {
            if (form.length() > 0) form.append('&');
            form.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            form.append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        byte[] bytes = form.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setFixedLengthStreamingMode(bytes.length);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(20_000);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        int code = connection.getResponseCode();
        String body = read(connection, code);
        connection.disconnect();
        return new HttpResponse(code, body);
    }

    private static void revoke(String token) throws Exception {
        LinkedHashMap<String,String> fields = new LinkedHashMap<>();
        fields.put("token", token);
        HttpResponse response = postForm(REVOKE_ENDPOINT, fields);
        if (response.code < 200 || response.code >= 300) {
            throw new Exception("Google 권한 해제 실패 (HTTP " + response.code + "): " + responseError(response.body));
        }
    }

    private static String read(HttpURLConnection connection, int code) throws Exception {
        InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() < 16_384) out.append(line);
            }
        }
        return out.toString();
    }

    private static String responseError(String body) {
        try {
            JSONObject json = new JSONObject(body);
            String description = json.optString("error_description", "");
            if (!description.isEmpty()) return description;
            Object error = json.opt("error");
            if (error instanceof JSONObject) return ((JSONObject) error).optString("message", "Google API 오류");
            if (error != null) return String.valueOf(error);
        } catch (Throwable ignored) {}
        return body == null || body.isEmpty() ? "응답 내용 없음" : body.substring(0, Math.min(240, body.length()));
    }

    private static String randomBase64Url(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return base64Url(value);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static void deliver(Callback callback, boolean success, String message) {
        if (callback == null) return;
        new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(success, message));
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value == null ? "0" : value); }
        catch (Throwable ignored) { return 0; }
    }

    private static boolean notEmpty(String value) { return value != null && !value.isEmpty(); }
    private static String safe(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? (error == null ? "알 수 없는 오류" : error.getClass().getSimpleName()) : message;
    }

    private static final class HttpResponse {
        final int code;
        final String body;
        HttpResponse(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }
}
