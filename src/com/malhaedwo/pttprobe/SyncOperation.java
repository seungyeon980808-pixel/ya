package com.malhaedwo.pttprobe;

public final class SyncOperation {
    public long id;
    public String itemUuid;
    public String operation;
    public int itemVersion;
    public long updatedAt;
    public int attempts;
    public String payload;
    public String lastError;
}
