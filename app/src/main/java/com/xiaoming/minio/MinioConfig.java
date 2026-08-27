package com.xiaoming.minio;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import io.minio.MinioClient;

final class MinioConfig {
    static final String DEFAULT_ENDPOINT = "http://10.200.100.7:9000";
    static final String DEFAULT_ACCESS_KEY = "minioadmin";
    static final String DEFAULT_SECRET_KEY = "minioadmin";
    static final String DEFAULT_BUCKET = "padlogs";
    static final String DEFAULT_OBJECT = "img_banner_01.png";

    private static final String PREFS = "minio_config";

    String endpoint;
    String accessKey;
    String secretKey;
    String bucket;
    String objectName;
    boolean ignoreCert;

    static MinioConfig defaults() {
        MinioConfig config = new MinioConfig();
        config.endpoint = DEFAULT_ENDPOINT;
        config.accessKey = DEFAULT_ACCESS_KEY;
        config.secretKey = DEFAULT_SECRET_KEY;
        config.bucket = DEFAULT_BUCKET;
        config.objectName = DEFAULT_OBJECT;
        config.ignoreCert = true;
        return config;
    }

    static MinioConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        MinioConfig config = defaults();
        config.endpoint = prefs.getString("endpoint", config.endpoint);
        config.accessKey = prefs.getString("accessKey", config.accessKey);
        config.secretKey = prefs.getString("secretKey", config.secretKey);
        config.bucket = prefs.getString("bucket", config.bucket);
        config.objectName = prefs.getString("objectName", config.objectName);
        config.ignoreCert = prefs.getBoolean("ignoreCert", config.ignoreCert);
        return config;
    }

    void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("endpoint", endpoint)
                .putString("accessKey", accessKey)
                .putString("secretKey", secretKey)
                .putString("bucket", bucket)
                .putString("objectName", objectName)
                .putBoolean("ignoreCert", ignoreCert)
                .apply();
    }

    String requireEndpoint() {
        if (TextUtils.isEmpty(endpoint)) {
            throw new IllegalArgumentException("endpoint");
        }
        return endpoint.trim();
    }

    String requireBucket() {
        if (TextUtils.isEmpty(bucket)) {
            throw new IllegalArgumentException("bucket");
        }
        return bucket.trim();
    }

    String requireObject() {
        if (TextUtils.isEmpty(objectName)) {
            throw new IllegalArgumentException("object");
        }
        return objectName.trim();
    }

    MinioClient createClient() throws Exception {
        MinioClient client = new MinioClient(
                requireEndpoint(),
                accessKey == null ? "" : accessKey.trim(),
                secretKey == null ? "" : secretKey.trim());
        client.setTimeout(15_000, 60_000, 60_000);
        client.setAppInfo("MinioForAndroid", "1.0");
        if (ignoreCert) {
            client.ignoreCertCheck();
        }
        return client;
    }
}
