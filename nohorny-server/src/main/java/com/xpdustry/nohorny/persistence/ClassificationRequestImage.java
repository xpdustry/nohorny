// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

/** Projection of {@link ClassificationRequest} with only the image blob and its media type. */
public interface ClassificationRequestImage {

    String getImageMediaType();

    byte[] getImage();
}
