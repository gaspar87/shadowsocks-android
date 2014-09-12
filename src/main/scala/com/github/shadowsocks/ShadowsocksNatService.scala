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

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content._
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os._
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.analytics.tracking.android.{Fields, MapBuilder, EasyTracker}
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import org.apache.http.conn.util.InetAddressUtils
import scala.collection._
import java.util.{TimerTask, Timer}
import android.net.TrafficStats
import scala.concurrent.ops._
import com.github.shadowsocks.utils._
import scala.Some
import android.graphics.Color
import com.github.shadowsocks.aidl.Config
import scala.collection.mutable.ArrayBuffer
import java.io.File
import com.github.tunnelar._

case class TrafficStat(tx: Long, rx: Long, timestamp: Long)

class ShadowsocksNatService extends NatService {

  def startShadowsocksDaemon() {
    fillACLList()    

    val args = (Path.BASE +
      "ss-local -b 127.0.0.1 -s '%s' -p '%d' -l '%d' -k ''%s' -m '%s' -f " +
      Path.BASE + "ss-local.pid")
      .format(config.proxy, config.remotePort, config.localPort, config.sitekey, config.encMethod)
    val cmd =  if (config.isGFWList && isACLEnabled) args + " --acl " + Path.BASE + "chn.acl" else args
    if (BuildConfig.DEBUG) Log.d(TAG, cmd)
    Console.runCommand(cmd)	
  }

  def startDnsDaemon() {
    val cmd = if (config.isUdpDns) {
      (Path.BASE +
        "ss-tunnel -b 127.0.0.1 -s '%s' -p '%d' -l '%d' -k '%s' -m '%s' -L 8.8.8.8:53 -u -f " +
        Path.BASE + "ss-tunnel.pid")
        .format(config.proxy, config.remotePort, 8153, config.sitekey, config.encMethod)
    } else {
      val conf = {
        if (config.isGFWList)
          ConfigUtils.PDNSD_BYPASS.format("127.0.0.1", getString(R.string.exclude))
        else
          ConfigUtils.PDNSD.format("127.0.0.1")
      }
      ConfigUtils.printToFile(new File(Path.BASE + "pdnsd.conf"))(p => {
         p.println(conf)
      })
      Path.BASE + "pdnsd -c " + Path.BASE + "pdnsd.conf"
    }
    if (BuildConfig.DEBUG) Log.d(TAG, cmd)
    Console.runRootCommand(cmd)
  }    

  /** Called when the activity is first created. */
  override def handleConnection: Boolean = {

    startShadowsocksDaemon()
    startDnsDaemon()
    startRedsocksDaemon()
    setupIptables
    flushDNS()

    true
  }
  
  def killProcesses() {
    Console.runRootCommand(Utils.getIptables + " -t nat -F OUTPUT")

    val ab = new ArrayBuffer[String]

    ab.append("kill -9 `cat " + Path.BASE +"redsocks.pid`")
    ab.append("killall -9 redsocks")
    ab.append("kill -9 `cat " + Path.BASE + "ss-tunnel.pid`")
    ab.append("killall -9 ss-tunnel")
    ab.append("kill -15 `cat " + Path.BASE + "pdnsd.pid`")
    ab.append("killall -15 pdnsd")

    Console.runRootCommand(ab.toArray)
  }  
}
