package com.malhaedwo.pttprobe;

public final class ParsedCommand {
    public final String kind;
    public final String title;
    public final long startAt;
    public final long endAt;
    public final long reminderAt;
    public final boolean needsReview;
    public final String explanation;

    public ParsedCommand(String kind, String title, long startAt, long endAt,
                         long reminderAt, boolean needsReview, String explanation) {
        this.kind = kind;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reminderAt = reminderAt;
        this.needsReview = needsReview;
        this.explanation = explanation;
    }
}
