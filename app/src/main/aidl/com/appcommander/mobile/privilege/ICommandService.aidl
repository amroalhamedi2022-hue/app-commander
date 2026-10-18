package com.appcommander.mobile.privilege;

interface ICommandService {
    void destroy() = 16777114;
    String execute(String command) = 1;
}
