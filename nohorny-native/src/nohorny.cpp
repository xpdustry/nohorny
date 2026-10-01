// SPDX-License-Identifier: MIT
#include "nohorny.h"

#include <algorithm>
#include <optional>
#include <stdexcept>
#include <string>

#include <opencv2/core.hpp>
#include <opencv2/dnn.hpp>
#include <opencv2/imgproc.hpp>

// Matches the preprocessing of the ViT models, 224 by 224 images with .5 mean and std
static constexpr int INPUT_SIZE = 224;
static constexpr double INPUT_MEAN = 127.5;
static constexpr double INPUT_SCALE = 1.0 / 127.5;

struct nh_classifier {
    cv::dnn::Net net;
};

namespace {

thread_local std::optional<std::string> last_error;

// C++ exceptions must never cross the C boundary
template <typename R, typename F>
R guard(R fallback, F&& function) noexcept {
    try {
        return function();
    } catch (const std::exception& e) {
        last_error = e.what();
    } catch (...) {
        last_error = "Unknown native error";
    }
    return fallback;
}

} // namespace

nh_classifier* nh_classifier_create(const char* model_path) {
    return guard<nh_classifier*>(nullptr, [&] {
        auto net = cv::dnn::readNetFromONNX(model_path);
        if (net.empty()) {
            throw std::runtime_error(std::string("Failed to load the model at ") + model_path);
        }
        return new nh_classifier{std::move(net)};
    });
}

int32_t nh_classifier_classify(
        nh_classifier* classifier,
        const uint32_t* pixels,
        int32_t width,
        int32_t height,
        float* scores,
        int32_t capacity) {
    return guard<int32_t>(-1, [&] {
        // Native-endian ARGB ints are BGRA bytes on little-endian hosts
        const cv::Mat bgra(height, width, CV_8UC4, const_cast<uint32_t*>(pixels));
        cv::Mat rgb;
        cv::cvtColor(bgra, rgb, cv::COLOR_BGRA2RGB);

        const auto blob = cv::dnn::blobFromImage(
                rgb, INPUT_SCALE, cv::Size(INPUT_SIZE, INPUT_SIZE), cv::Scalar::all(INPUT_MEAN), false, false);
        classifier->net.setInput(blob);

        cv::Mat logits = classifier->net.forward().reshape(1, 1);
        logits.convertTo(logits, CV_32F);
        cv::Mat probabilities;
        cv::exp(logits - cv::Scalar(*std::max_element(logits.begin<float>(), logits.end<float>())), probabilities);
        probabilities /= cv::sum(probabilities)[0];

        const auto count = static_cast<int32_t>(probabilities.total());
        std::copy_n(probabilities.ptr<float>(), std::min(count, capacity), scores);
        return count;
    });
}

void nh_classifier_close(nh_classifier* classifier) {
    delete classifier;
}

const char* nh_error_get(void) {
    return last_error ? last_error->c_str() : nullptr;
}

void nh_error_clear(void) {
    last_error.reset();
}
