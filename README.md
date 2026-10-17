# Rhythm Sorter Linux

A Hebrew desktop utility for Linux that analyzes local music files using the Rhythm engine and copies each song into **one** mood folder. Original files are not moved or changed.

## Download

Open [Releases](https://github.com/avicp2023/app/releases) and download the latest `.deb` package for 64-bit Linux. This package includes its Java runtime.

## How the sorting rule works

If a song matches several categories and **קצבי** is among them, it is placed only in `קצבי`. Otherwise, the app chooses the strongest matching category. Songs that cannot be analyzed go into `לא נותח`.

Choose separate source and destination folders. Existing destination files are never overwritten.

## Build source

The Linux sorter lives in the `rhythm-sorter-linux` branch. Its GitHub Actions workflow builds a Debian package using the Rhythm analysis engine pinned to a fixed upstream revision. See [third-party notices](https://github.com/avicp2023/app/blob/rhythm-sorter-linux/NOTICE.md) for license information.
