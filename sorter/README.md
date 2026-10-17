# Rhythm Sorter Linux

A small Hebrew desktop application for Linux that scans a music folder, analyzes tracks with the Rhythm audio-analysis engine, and **copies** them into mood folders. Original files are not moved or changed.

## Build

The GitHub Actions workflow pins the Rhythm engine source to commit `13f7f3ec0ceb01ff34446a09988cf852492ca230`, supplies its analyzer and models to this module, runs engine tests, and creates a self-contained Debian package for x86-64 Linux.

Expected output: `rhythm-sorter_1.0.0-1_amd64.deb`.

## Use

Choose a source music folder and a different destination folder, then click **מיין מוזיקה**. The program creates folders such as `רגוע`, `קצבי`, `שמח`, `מרגש`, `ריכוז`, `אימון`, and `לילה`. Files it cannot analyze are copied to `לא נותח`. Existing destination files are never overwritten.

Supported input formats depend on the bundled Java Sound/FFmpeg decoders. This build targets Linux x86-64 and requires no separate Java installation because the Debian package bundles a runtime.
