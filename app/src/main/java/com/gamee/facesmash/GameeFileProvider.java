package com.gamee.facesmash;

import android.support.v4.content.FileProvider;

/*
 * Define our own FileProvider class as to not conflict with
 * FileProvider's possibly declared in imported dependencies.
 * see https://stackoverflow.com/a/38858040/2508150
 * and https://commonsware.com/blog/2017/06/27/fileprovider-libraries.html
 */
public class GameeFileProvider extends FileProvider {
}
