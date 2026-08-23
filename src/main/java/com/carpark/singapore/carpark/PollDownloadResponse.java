package com.carpark.singapore.carpark;

/** Response shape of data.gov.sg's dataset poll-download endpoint. */
record PollDownloadResponse(int code, PollDownloadData data, String errorMsg) {

    record PollDownloadData(String status, String url) {
    }

    boolean isDownloadReady() {
        return data != null && "DOWNLOAD_SUCCESS".equals(data.status()) && data.url() != null;
    }
}
