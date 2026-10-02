// SPDX-License-Identifier: MIT
#ifndef NOHORNY_H
#define NOHORNY_H

#include <stdint.h>

#if defined(_WIN32)
#define NH_API __declspec(dllexport)
#else
#define NH_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

/// A ViT image classifier backed by an ONNX model. Instances are not thread-safe.
typedef struct nh_classifier nh_classifier;

/// Loads the ONNX model at the given path. Returns NULL on failure.
NH_API nh_classifier* nh_classifier_create(const char* model_path);

/// Classifies an image of width * height pixels packed as native-endian ARGB ints (like Java's TYPE_INT_ARGB).
/// Writes up to capacity softmax probabilities into scores and returns the total number of labels,
/// or -1 on failure.
NH_API int32_t nh_classifier_classify(
        nh_classifier* classifier,
        const uint32_t* argb_pixels,
        int32_t width,
        int32_t height,
        float* scores,
        int32_t capacity);

/// Releases the classifier. Accepts NULL.
NH_API void nh_classifier_close(nh_classifier* classifier);

/// Returns the last error raised on the calling thread, or NULL if none.
/// The pointer stays valid until the next failing call or nh_error_clear on the calling thread.
NH_API const char* nh_error_get(void);

/// Clears the last error raised on the calling thread.
NH_API void nh_error_clear(void);

#ifdef __cplusplus
}
#endif

#endif // NOHORNY_H
