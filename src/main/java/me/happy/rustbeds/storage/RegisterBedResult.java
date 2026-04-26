package me.happy.rustbeds.storage;

public enum RegisterBedResult {
    CREATED,
    ADDED_OWNER,
    ALREADY_REGISTERED,
    EXCLUSIVE_CONFLICT,
    MAX_BEDS_REACHED
}
