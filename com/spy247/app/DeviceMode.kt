package com.spy247.app

/**
 * Enum to define the device mode - either Controller (your phone) 
 * or FamilyMember (the target spyed device)
 */
enum class DeviceMode {
    CONTROLLER,      // Your main hub phone
    FAMILY_MEMBER    // The target device being spied on
}