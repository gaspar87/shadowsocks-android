/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package com.github.shadowsocks

import android.app._
import android.content._
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os._
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.analytics.tracking.android.{Fields, MapBuilder, EasyTracker}
import android.net.VpnService
import org.apache.http.conn.util.InetAddressUtils
import android.os.Message
import scala.concurrent.ops._
import org.apache.commons.net.util.SubnetUtils
import java.net.InetAddress
import com.github.shadowsocks.utils._
import scala.Some
import com.github.shadowsocks.aidl.{IShadowsocksService, Config}
import scala.collection.mutable.ArrayBuffer
import com.github.shadowsocks._
import java.io.File

class ShadowsocksVpnService extends CustomVpnService {

  def startShadowsocksDaemon() {
    val cmd: String = (Path.BASE +
      "ss-local -b 127.0.0.1 -s '%s' -p '%d' -l '%d' -k '%s' -m '%s' -u -f " +
      Path.BASE + "ss-local.pid")
      .format(config.proxy, config.remotePort, config.localPort, config.sitekey, config.encMethod)
    if (BuildConfig.DEBUG) Log.d(TAG, cmd)
    System.exec(cmd)
  }  

  /** Called when the activity is first created. */
  override def handleConnection: Boolean = {
    startVpn()
    startShadowsocksDaemon()
    if (!config.isUdpDns) startDnsDaemon()
    true
  }

  override def killProcesses() {
    val ab = new ArrayBuffer[String]

    ab.append("kill -9 `cat " + Path.BASE + "ss-local.pid`")
    ab.append("killall -9 ss-local")
    ab.append("kill -9 `cat " + Path.BASE + "tun2socks.pid`")
    ab.append("killall -9 tun2socks")
    ab.append("kill -15 `cat " + Path.BASE + "pdnsd.pid`")
    ab.append("killall -15 pdnsd")

    Console.runCommand(ab.toArray)
  }
}
