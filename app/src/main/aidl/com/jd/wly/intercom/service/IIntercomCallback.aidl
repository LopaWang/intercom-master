package com.jd.wly.intercom.service;

interface IIntercomCallback {

    void findNewUser(String ipAddress);
    void removeUser(String ipAddress);
    void isSpeak(String ipAddress);
    void isNotSpeak(String ipAddress);
}
