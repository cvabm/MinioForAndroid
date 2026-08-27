package com.xiaoming.minio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.xiaoming.minio.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

import io.minio.ErrorCode;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ObjectAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private boolean busy;

    private final ActivityResultLauncher<String> pickFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        adapter = new ObjectAdapter(new ObjectAdapter.Listener() {
            @Override
            public void onClick(ObjectRow row) {
                binding.objectName.setText(row.name);
                appendLog("已选中对象: " + row.name);
            }

            @Override
            public void onLongClick(View anchor, ObjectRow row) {
                binding.objectName.setText(row.name);
                showObjectMenu(anchor, row);
            }
        });
        binding.objectList.setLayoutManager(new LinearLayoutManager(this));
        binding.objectList.setAdapter(adapter);
        binding.objectList.setNestedScrollingEnabled(false);

        bindConfig(MinioConfig.load(this));
        appendLog("就绪。先保存或确认连接配置，再执行操作。");

        binding.saveConfig.setOnClickListener(v -> {
            readForm().save(this);
            toast(getString(R.string.saved));
            appendLog("配置已保存");
        });
        binding.restoreDefaults.setOnClickListener(v -> {
            MinioConfig defaults = MinioConfig.defaults();
            bindConfig(defaults);
            defaults.save(this);
            toast(getString(R.string.restored));
            appendLog("已恢复默认配置");
        });
        binding.testConnection.setOnClickListener(v -> testConnection());
        binding.listBuckets.setOnClickListener(v -> listBuckets());
        binding.createBucket.setOnClickListener(v -> createBucket());
        binding.listObjects.setOnClickListener(v -> listObjects());
        binding.uploadSample.setOnClickListener(v -> uploadSample());
        binding.uploadFile.setOnClickListener(v -> pickFile.launch("*/*"));
        binding.downloadObject.setOnClickListener(v -> downloadObject(null));
        binding.objectUrl.setOnClickListener(v -> copyObjectUrl(null));
        binding.objectStat.setOnClickListener(v -> showObjectStat(null));
        binding.deleteObject.setOnClickListener(v -> confirmDelete(null));
        binding.clearLog.setOnClickListener(v -> binding.logView.setText(""));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void bindConfig(MinioConfig config) {
        binding.endpoint.setText(config.endpoint);
        binding.accessKey.setText(config.accessKey);
        binding.secretKey.setText(config.secretKey);
        binding.bucket.setText(config.bucket);
        binding.objectName.setText(config.objectName);
        binding.ignoreCert.setChecked(config.ignoreCert);
    }

    private MinioConfig readForm() {
        MinioConfig config = new MinioConfig();
        config.endpoint = text(binding.endpoint);
        config.accessKey = text(binding.accessKey);
        config.secretKey = text(binding.secretKey);
        config.bucket = text(binding.bucket);
        config.objectName = text(binding.objectName);
        config.ignoreCert = binding.ignoreCert.isChecked();
        return config;
    }

    private static String text(android.widget.EditText view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void testConnection() {
        runIo("正在测试连接…", config -> {
            MinioClient client = config.createClient();
            List<Bucket> buckets = client.listBuckets();
            StringBuilder sb = new StringBuilder("连接成功，共 ").append(buckets.size()).append(" 个桶");
            for (Bucket bucket : buckets) {
                sb.append("\n  - ").append(bucket.name());
            }
            if (!TextUtils.isEmpty(config.bucket)) {
                boolean exists = client.bucketExists(config.bucket.trim());
                sb.append("\n当前桶 ").append(config.bucket).append(exists ? " 存在" : " 不存在");
            }
            return sb.toString();
        }, new IoResultUi() {
            @Override
            public void onSuccess(String result) {
                toast(getString(R.string.connect_success));
            }

            @Override
            public void onFailure(String detail) {
                showErrorDialog(getString(R.string.connect_failed_title), detail);
            }
        });
    }

    private void listBuckets() {
        runIo("正在列出桶…", config -> {
            MinioClient client = config.createClient();
            List<Bucket> buckets = client.listBuckets();
            List<String> names = new ArrayList<>();
            StringBuilder sb = new StringBuilder("桶列表 (").append(buckets.size()).append(")");
            for (Bucket bucket : buckets) {
                names.add(bucket.name());
                sb.append("\n  - ").append(bucket.name());
            }
            runOnUiThread(() -> showBucketPicker(names));
            return sb.toString();
        });
    }

    private void showBucketPicker(List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_bucket_title)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    binding.bucket.setText(names.get(which));
                    appendLog("已填入 Bucket: " + names.get(which));
                })
                .show();
    }

    private void createBucket() {
        runIo("正在创建桶…", config -> {
            String bucket = config.requireBucket();
            MinioClient client = config.createClient();
            if (client.bucketExists(bucket)) {
                return "桶已存在: " + bucket;
            }
            client.makeBucket(bucket);
            return "已创建桶: " + bucket;
        });
    }

    private void listObjects() {
        runIo("正在列出对象…", config -> {
            String bucket = config.requireBucket();
            MinioClient client = config.createClient();
            List<ObjectRow> rows = new ArrayList<>();
            int count = 0;
            for (Result<Item> result : client.listObjects(bucket, "", true)) {
                Item item = result.get();
                count++;
                String modified = "";
                long size = 0;
                if (!item.isDir()) {
                    size = item.objectSize();
                    try {
                        Date date = item.lastModified();
                        if (date != null) {
                            modified = dateFmt.format(date);
                        }
                    } catch (Exception ignored) {
                    }
                }
                rows.add(new ObjectRow(item.objectName(), size, modified, item.isDir()));
            }
            final List<ObjectRow> uiRows = rows;
            runOnUiThread(() -> {
                adapter.submit(uiRows);
                binding.emptyObjects.setVisibility(uiRows.isEmpty() ? View.VISIBLE : View.GONE);
            });
            return "列出对象完成，共 " + count + " 项（桶: " + bucket + "）";
        });
    }

    private void uploadSample() {
        runIo("正在上传示例图…", config -> {
            String bucket = config.requireBucket();
            String objectName = TextUtils.isEmpty(config.objectName)
                    ? MinioConfig.DEFAULT_OBJECT
                    : config.objectName.trim();
            File file = copyAsset("img_banner_01.png", "sample.png");
            MinioClient client = config.createClient();
            ensureBucket(client, bucket);
            client.putObject(bucket, objectName, file.getAbsolutePath());
            return "上传成功\n  桶: " + bucket + "\n  对象: " + objectName + "\n  本地: " + file.getAbsolutePath();
        });
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            appendLog("未选择文件");
            return;
        }
        runIo("正在上传文件…", config -> {
            String bucket = config.requireBucket();
            String displayName = queryDisplayName(uri);
            String objectName = TextUtils.isEmpty(config.objectName) ? displayName : config.objectName.trim();
            File file = copyUri(uri, displayName);
            MinioClient client = config.createClient();
            ensureBucket(client, bucket);
            client.putObject(bucket, objectName, file.getAbsolutePath());
            final String filled = objectName;
            runOnUiThread(() -> binding.objectName.setText(filled));
            return "上传成功\n  桶: " + bucket + "\n  对象: " + objectName + "\n  文件: " + displayName;
        });
    }

    private void downloadObject(@Nullable String objectOverride) {
        runIo("正在下载…", config -> {
            String bucket = config.requireBucket();
            String objectName = objectOverride != null ? objectOverride : config.requireObject();
            MinioClient client = config.createClient();
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                dir = getFilesDir();
            }
            String fileName = objectName.contains("/")
                    ? objectName.substring(objectName.lastIndexOf('/') + 1)
                    : objectName;
            File dest = new File(dir, fileName);
            try (InputStream in = client.getObject(bucket, objectName);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
            return "下载成功\n  对象: " + objectName + "\n  保存到: " + dest.getAbsolutePath();
        });
    }

    private void copyObjectUrl(@Nullable String objectOverride) {
        runIo("正在获取地址…", config -> {
            String bucket = config.requireBucket();
            String objectName = objectOverride != null ? objectOverride : config.requireObject();
            MinioClient client = config.createClient();
            String url = client.getObjectUrl(bucket, objectName);
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("minio-url", url));
                }
            });
            return "对象地址（已复制）\n" + url;
        });
    }

    private void showObjectStat(@Nullable String objectOverride) {
        runIo("正在读取对象信息…", config -> {
            String bucket = config.requireBucket();
            String objectName = objectOverride != null ? objectOverride : config.requireObject();
            MinioClient client = config.createClient();
            ObjectStat stat = client.statObject(bucket, objectName);
            return "对象信息\n  桶: " + stat.bucketName()
                    + "\n  名称: " + stat.name()
                    + "\n  大小: " + ObjectAdapter.formatSize(stat.length())
                    + "\n  类型: " + stat.contentType()
                    + "\n  ETag: " + stat.etag()
                    + "\n  时间: " + (stat.createdTime() == null ? "-" : dateFmt.format(stat.createdTime()));
        });
    }

    private void confirmDelete(@Nullable String objectOverride) {
        MinioConfig config = readForm();
        final String objectName;
        try {
            objectName = objectOverride != null ? objectOverride : config.requireObject();
            config.requireBucket();
        } catch (IllegalArgumentException e) {
            toast(missingText(e));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_message, objectName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteObject(objectName))
                .show();
    }

    private void deleteObject(String objectName) {
        runIo("正在删除…", config -> {
            String bucket = config.requireBucket();
            MinioClient client = config.createClient();
            client.removeObject(bucket, objectName);
            return "已删除对象: " + objectName;
        });
    }

    private void showObjectMenu(View anchor, ObjectRow row) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_download);
        menu.getMenu().add(0, 2, 1, R.string.menu_url);
        menu.getMenu().add(0, 3, 2, R.string.menu_stat);
        menu.getMenu().add(0, 4, 3, R.string.menu_delete);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                downloadObject(row.name);
            } else if (id == 2) {
                copyObjectUrl(row.name);
            } else if (id == 3) {
                showObjectStat(row.name);
            } else if (id == 4) {
                confirmDelete(row.name);
            }
            return true;
        });
        menu.show();
    }

    private interface IoWork {
        String run(MinioConfig config) throws Exception;
    }

    private interface IoResultUi {
        void onSuccess(String result);

        void onFailure(String detail);
    }

    private void runIo(String busyMessage, IoWork work) {
        runIo(busyMessage, work, null);
    }

    private void runIo(String busyMessage, IoWork work, IoResultUi ui) {
        if (busy) {
            return;
        }
        MinioConfig config = readForm();
        config.save(this);
        setBusy(true, busyMessage);
        io.execute(() -> {
            try {
                String result = work.run(config);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    appendLog(result);
                    if (ui != null) {
                        ui.onSuccess(result);
                    } else {
                        toast("完成");
                    }
                });
            } catch (IllegalArgumentException e) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    String msg = missingText(e);
                    appendLog("失败: " + msg);
                    if (ui != null) {
                        ui.onFailure(msg);
                    } else {
                        toast(msg);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    String msg = describeError(e);
                    appendLog("失败: " + msg);
                    if (ui != null) {
                        ui.onFailure(msg);
                    } else {
                        toast("失败: " + msg);
                    }
                });
            }
        });
    }

    private String missingText(IllegalArgumentException e) {
        String key = e.getMessage();
        if ("endpoint".equals(key)) {
            return getString(R.string.need_endpoint);
        }
        if ("bucket".equals(key)) {
            return getString(R.string.need_bucket);
        }
        if ("object".equals(key)) {
            return getString(R.string.need_object);
        }
        return key;
    }

    private String describeError(Throwable error) {
        Throwable t = error;
        for (int i = 0; t != null && i < 8; i++) {
            if (t instanceof UnknownHostException) {
                return getString(R.string.err_unknown_host);
            }
            if (t instanceof ConnectException || t instanceof NoRouteToHostException) {
                return getString(R.string.err_connect);
            }
            if (t instanceof SocketTimeoutException) {
                return getString(R.string.err_timeout);
            }
            if (t instanceof SSLException) {
                return getString(R.string.err_ssl);
            }
            if (t instanceof InvalidEndpointException || t instanceof InvalidPortException) {
                return getString(R.string.err_endpoint);
            }
            if (t instanceof ErrorResponseException) {
                ErrorResponseException ere = (ErrorResponseException) t;
                ErrorCode code = ere.errorResponse() == null ? null : ere.errorResponse().errorCode();
                if (code == ErrorCode.INVALID_ACCESS_KEY_ID || code == ErrorCode.SIGNATURE_DOES_NOT_MATCH) {
                    return getString(R.string.err_auth);
                }
                if (code == ErrorCode.ACCESS_DENIED) {
                    return getString(R.string.err_denied);
                }
                String serverMsg = ere.getMessage();
                if (TextUtils.isEmpty(serverMsg) && code != null) {
                    serverMsg = code.message();
                }
                return getString(R.string.err_generic, TextUtils.isEmpty(serverMsg) ? t.getClass().getSimpleName() : serverMsg);
            }
            t = t.getCause();
        }
        String msg = error.getMessage();
        if (TextUtils.isEmpty(msg)) {
            msg = error.getClass().getSimpleName();
        }
        return getString(R.string.err_generic, msg);
    }

    private void showErrorDialog(String title, String message) {
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        binding.busyOverlay.setVisibility(value ? View.VISIBLE : View.GONE);
        if (message != null) {
            binding.busyText.setText(message);
        }
    }

    private void appendLog(String message) {
        String line = "[" + timeFmt.format(new Date()) + "] " + message;
        CharSequence old = binding.logView.getText();
        if (TextUtils.isEmpty(old)) {
            binding.logView.setText(line);
        } else {
            binding.logView.setText(old + "\n" + line);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static void ensureBucket(MinioClient client, String bucket) throws Exception {
        if (!client.bucketExists(bucket)) {
            client.makeBucket(bucket);
        }
    }

    private File copyAsset(String assetName, String outName) throws Exception {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        File out = new File(dir, outName);
        try (InputStream in = getAssets().open(assetName);
             OutputStream os = new FileOutputStream(out)) {
            copyStream(in, os);
        }
        return out;
    }

    private File copyUri(Uri uri, String name) throws Exception {
        File out = new File(getCacheDir(), name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                throw new IllegalStateException("无法读取所选文件");
            }
            copyStream(in, os);
        }
        return out;
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private String queryDisplayName(Uri uri) {
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return TextUtils.isEmpty(last) ? "upload.bin" : last;
    }
}
