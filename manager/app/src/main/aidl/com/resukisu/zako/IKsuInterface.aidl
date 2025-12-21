// IKsuInterface.aidl
package com.resukisu.zako;

import android.content.pm.PackageInfo;
import java.util.List;

interface IKsuInterface {
    int getPackageCount();
    List<PackageInfo> getPackages(int start, int maxCount);
}