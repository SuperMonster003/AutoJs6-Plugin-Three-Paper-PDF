package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * A stand-in for a documents provider (appendix D Q106 = B device tests). It is declared by the test APK, so it runs
 * in the test APK's own process under the test APK's uid: only a provider of another uid makes the system record URI
 * grants for the plugin, which a provider of the plugin itself never does. Reading needs a grant (the read permission
 * is one the plugin does not hold); {@link #call} lets the test create a PDF, grant the plugin read access (persistable
 * or not, as the system file picker would), revoke the grants and delete the file.
 * <p>
 * Java on purpose: the test APK leaves out the Kotlin standard library, which only the plugin's process provides.
 * <p>
 * zh-CN: 文档提供方的替身 (附录 D Q106 = B 设备测试). 由测试 APK 声明, 因此运行在测试 APK 自己的进程中, 使用测试 APK 的 uid: 只有其他
 * uid 的提供方, 系统才会为插件记录 URI 授权, 插件自己的提供方不会. 读取需要授权 (读取权限是插件不持有的权限); 测试经 {@link #call}
 * 创建 PDF, 像系统文件选择器那样授予插件读取权限 (可持久或不可持久), 撤销授权并删除文件.
 * <p>
 * 有意使用 Java: 测试 APK 不打包 Kotlin 标准库, 只有插件进程提供它.
 */
public final class GrantingTestProvider extends ContentProvider {

    /** Copies the fixture {@value #ASSET} to the document named {@code arg}; answers its uri. zh-CN: 把样例复制为名为 {@code arg} 的文档; 返回其 uri. */
    public static final String METHOD_CREATE = "create";

    /** Grants package {@link #EXTRA_PACKAGE} read access to uri {@code arg}. zh-CN: 授予 {@link #EXTRA_PACKAGE} 读取 uri {@code arg} 的权限. */
    public static final String METHOD_GRANT = "grant";

    /** Revokes every grant of uri {@code arg}, persisted ones included. zh-CN: 撤销 uri {@code arg} 的全部授权, 包括已持久化的. */
    public static final String METHOD_REVOKE = "revoke";

    /** Deletes the file of uri {@code arg}, leaving the grants. zh-CN: 删除 uri {@code arg} 的文件, 保留授权. */
    public static final String METHOD_DELETE = "delete";

    /** Revokes the grants of every document and deletes the files. zh-CN: 撤销全部文档的授权并删除文件. */
    public static final String METHOD_RESET = "reset";

    public static final String EXTRA_URI = "uri";

    public static final String EXTRA_PACKAGE = "package";

    /** Whether the grant may be persisted, as from the system file picker. zh-CN: 授权是否可持久化, 与系统文件选择器相同. */
    public static final String EXTRA_PERSISTABLE = "persistable";

    public static final String ASSET = "text.pdf";

    private static final String SEGMENT = "document";

    private static final int ALL_MODES = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        try {
            switch (method) {
                case METHOD_CREATE: {
                    File file = new File(directory(), checkName(arg));
                    try (InputStream input = getContext().getAssets().open(ASSET); OutputStream output = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
                    }
                    result.putString(EXTRA_URI, uriOf(file.getName()).toString());
                    break;
                }
                case METHOD_GRANT: {
                    int modes = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    if (extras.getBoolean(EXTRA_PERSISTABLE)) modes |= Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
                    getContext().grantUriPermission(extras.getString(EXTRA_PACKAGE), Uri.parse(arg), modes);
                    break;
                }
                case METHOD_REVOKE:
                    getContext().revokeUriPermission(Uri.parse(arg), ALL_MODES);
                    break;
                case METHOD_DELETE: {
                    File file = fileOf(Uri.parse(arg));
                    if (file == null || !file.delete()) throw new IOException("not deleted: " + arg);
                    break;
                }
                case METHOD_RESET: {
                    File[] files = directory().listFiles();
                    if (files != null) {
                        for (File file : files) {
                            getContext().revokeUriPermission(uriOf(file.getName()), ALL_MODES);
                            if (!file.delete()) throw new IOException("not deleted: " + file);
                        }
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown method " + method);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = fileOf(uri);
        // Like a documents provider, an unknown document answers no cursor. zh-CN: 与文档提供方一样, 未知文档不返回游标.
        if (file == null || !file.isFile()) return null;
        String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileOf(uri);
        if (file == null || !file.isFile() || !"r".equals(mode)) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/pdf";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private File directory() {
        File directory = new File(getContext().getFilesDir(), "granting-documents");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("no directory " + directory);
        return directory;
    }

    private Uri uriOf(String name) {
        return new Uri.Builder().scheme("content").authority(getContext().getPackageName() + ".grants").appendPath(SEGMENT).appendPath(name).build();
    }

    private File fileOf(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !SEGMENT.equals(segments.get(0))) return null;
        return new File(directory(), checkName(segments.get(1)));
    }

    private static String checkName(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.startsWith(".")) throw new IllegalArgumentException("bad name " + name);
        return name;
    }

}
