#include "core/app.h"
#include "platform/touch.h"

#include <SDL.h>

struct touch_module_t {
    float pinch_accum = 0.0f;
    void handle_finger_down(SDL_Event* ev);
    void handle_finger_motion(SDL_Event* ev);
    void handle_finger_up(SDL_Event* ev);
    void handle_multigesture(SDL_Event* ev);
};

void touch_module_t::handle_finger_down(SDL_Event* ev) {
    if (ev->type != SDL_FINGERDOWN) {
        return;
    }

    platform_touch_start(&ev->tfinger);
}

void touch_module_t::handle_finger_motion(SDL_Event* ev) {
    if (ev->type != SDL_FINGERMOTION) {
        return;
    }

    platform_touch_move(&ev->tfinger);
}

void touch_module_t::handle_finger_up(SDL_Event* ev) {
    if (ev->type != SDL_FINGERUP) {
        return;
    }

    pinch_accum = 0.0f;
    platform_touch_end(&ev->tfinger);
}

void touch_module_t::handle_multigesture(SDL_Event* ev) {
    if (ev->type != SDL_MULTIGESTURE || ev->mgesture.numFingers != 2) {
        return;
    }

    pinch_accum += ev->mgesture.dDist;
    if (pinch_accum > 0.04f || pinch_accum < -0.04f) {
        SDL_Event wheel{};
        wheel.type = SDL_MOUSEWHEEL;
        wheel.wheel.y = (pinch_accum > 0.0f) ? 1 : -1;
        SDL_PushEvent(&wheel);
        pinch_accum = 0.0f;
    }
}

void ANK_REGISTER_APPLICATION_MODULE(register_touch_module) {
    static touch_module_t mod;
    g_app.register_touch_event_handler([&](void* ev) { mod.handle_finger_down((SDL_Event*)ev); });
    g_app.register_touch_event_handler([&](void* ev) { mod.handle_finger_motion((SDL_Event*)ev); });
    g_app.register_touch_event_handler([&](void* ev) { mod.handle_finger_up((SDL_Event*)ev); });
    g_app.register_touch_event_handler([&](void* ev) { mod.handle_multigesture((SDL_Event*)ev); });
}
