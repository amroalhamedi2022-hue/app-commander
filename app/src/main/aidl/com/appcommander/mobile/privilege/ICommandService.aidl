package com.appcommander.mobile.privilege;

interface ICommandService {
    void destroy() = 16777114;
    String execute(int operation, String packageName, String argument) = 1;
}
