package com.xiaoming.minio;

final class ObjectRow {
    final String name;
    final long size;
    final String modified;
    final boolean directory;

    ObjectRow(String name, long size, String modified, boolean directory) {
        this.name = name;
        this.size = size;
        this.modified = modified;
        this.directory = directory;
    }
}
