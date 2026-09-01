package com.westy.codmanager.order.domain;

/** Where the order came from. Drives the per-channel return rate later. */
public enum OrderSource {
    INSTAGRAM,
    TIKTOK,
    FACEBOOK,
    WEB_FORM,
    PHONE,
    OTHER
}
