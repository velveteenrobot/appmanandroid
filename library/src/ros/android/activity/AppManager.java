/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2011, Willow Garage, Inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of Willow Garage, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ros.android.activity;

import org.ros.node.topic.Subscriber;

import java.util.ArrayList;

import org.ros.exception.RosException;
import org.ros.exception.RemoteException;

import org.ros.message.MessageListener;
import org.ros.message.app_manager.App;
import org.ros.message.app_manager.AppList;

import org.ros.node.Node;
import org.ros.node.service.ServiceResponseListener;
import org.ros.node.service.ServiceClient;
import org.ros.internal.node.xmlrpc.XmlRpcTimeoutException;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.service.app_manager.ListApps;
import org.ros.service.app_manager.StartApp;
import org.ros.service.app_manager.StopApp;
import org.ros.message.app_manager.AppInstallationState;
import org.ros.message.app_manager.ExchangeApp;
import org.ros.service.app_manager.GetAppDetails;
import org.ros.service.app_manager.GetInstallationState;
import org.ros.service.app_manager.InstallApp;
import org.ros.service.app_manager.UninstallApp;
import android.util.Log;

/**
 * Interact with a remote ROS App Manager.
 *
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class AppManager {
  static public final String PACKAGE = "ros.android.activity";

  private final Node node;
  private AppList appList;
  private ArrayList<Subscriber> subscriptions;
  private NameResolver resolver;

  public interface TerminationCallback {
    public void onAppTermination();
  }

  private class TerminationCallbackInfo {
    public TerminationCallback callback;
    public String appname;
    public TerminationCallbackInfo(String appname, TerminationCallback callback) {
      this.appname = appname;
      this.callback = callback;
    }
  }

  private ArrayList<TerminationCallbackInfo> terminationCallbacks;

  public AppManager(Node node, NameResolver resolver)
      throws RosException {
    this.node = node;
    this.resolver = resolver;
    subscriptions = new ArrayList<Subscriber>();
    terminationCallbacks = new ArrayList<TerminationCallbackInfo>();
    addAppListCallback(new MessageListener<AppList>() {
      @Override
      public void onNewMessage(AppList message) {
        if (appList != null) {
          for (App a : appList.running_apps) {
            boolean stillRunning = false;
            for (App b : message.running_apps) {
              if (b.name.equals(a.name)) {
                stillRunning = true;
              }
            }
            if (!stillRunning) {
              Log.i("AppManager", "Terminate application: " + a.name);
              for (TerminationCallbackInfo c : terminationCallbacks) {
                if (c.appname == null) {
                  Log.i("AppManager", "Terminate callback called");
                  c.callback.onAppTermination();
                } else if (c.appname.equals(a.name)) {
                  Log.i("AppManager", "Terminate callback called");
                  c.callback.onAppTermination();
                }
              }
            }
          }
        }
        appList = message;
      }
    });

  }

  public void addTerminationCallback(String appname, TerminationCallback callback) {
    terminationCallbacks.add(new TerminationCallbackInfo(appname, callback));
  }

  
  
  public void addAppListCallback(MessageListener<AppList> callback) throws RosException {
    Subscriber<AppList> s = node.newSubscriber(resolver.resolve("app_list"), "app_manager/AppList");
    s.addMessageListener(callback);
    subscriptions.add(s);
  }

  public void addExchangeListCallback(MessageListener<AppInstallationState> callback) throws RosException {
    Subscriber<AppInstallationState> s = node.newSubscriber(resolver.resolve("exchange_app_list"), "app_manager/AppInstallationState");
    s.addMessageListener(callback);
    subscriptions.add(s);
  }

  public AppList getAppList() {
    return appList;
  }
  
  private final org.ros.internal.node.response.StatusCode ERROR_STATUS 
    = org.ros.internal.node.response.StatusCode.ERROR;

  public void listApps(final ServiceResponseListener<ListApps.Response> callback) {
    try {
      ServiceClient<ListApps.Request, ListApps.Response> listAppsClient =
        node.newServiceClient(resolver.resolve("list_apps"), "app_manager/ListApps");
      listAppsClient.call(new ListApps.Request(), callback);
    } catch (Throwable ex) {
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  public void listExchangeApps(boolean remoteUpdate, final ServiceResponseListener<GetInstallationState.Response> callback) {
    try {
      ServiceClient<GetInstallationState.Request, GetInstallationState.Response> listAppsClient =
        node.newServiceClient(resolver.resolve("list_exchange_apps"), "app_manager/GetInstallationState");
      GetInstallationState.Request request = new GetInstallationState.Request();
      request.remote_update = remoteUpdate;
      listAppsClient.call(request, callback);
    } catch (Throwable ex) {
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  public void startApp(final String appName,
                       final ServiceResponseListener<StartApp.Response> callback) {
    try {
      ServiceClient<StartApp.Request, StartApp.Response> startAppClient =
        node.newServiceClient(resolver.resolve("start_app"), "app_manager/StartApp");
      Log.i("AppManager", "Start app service client created");
      StartApp.Request request = new StartApp.Request();
      request.name = appName;
      startAppClient.call(request, callback);
      Log.i("AppManager", "Done call");
    } catch (Throwable ex) {
      Log.i("AppManager", "Start apps failed: " + ex.toString());
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  public void getAppDetails(final String appName,
                       final ServiceResponseListener<GetAppDetails.Response> callback) {
    try {
      ServiceClient<GetAppDetails.Request, GetAppDetails.Response> startAppClient =
        node.newServiceClient(resolver.resolve("get_app_details"), "app_manager/GetAppDetails");
      Log.i("AppManager", "Start app service client created");
      GetAppDetails.Request request = new GetAppDetails.Request();
      request.name = appName;
      startAppClient.call(request, callback);
      Log.i("AppManager", "Done call");
    } catch (Throwable ex) {
      Log.i("AppManager", "Get app details failed: " + ex.toString());
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  public void stopApp(final String appName, final ServiceResponseListener<StopApp.Response> callback) {
    try {
      ServiceClient<StopApp.Request, StopApp.Response> stopAppClient =
        node.newServiceClient(resolver.resolve("stop_app"), "app_manager/StopApp");
      StopApp.Request request = new StopApp.Request();
      request.name = appName;
      stopAppClient.call(request, callback);
    } catch (Throwable ex) {
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  public void installApp(final String appName,
                       final ServiceResponseListener<InstallApp.Response> callback) {
    try {
      ServiceClient<InstallApp.Request, InstallApp.Response> installAppClient =
        node.newServiceClient(resolver.resolve("install_app"), "app_manager/InstallApp");
      InstallApp.Request request = new InstallApp.Request();
      request.name = appName;
      installAppClient.call(request, callback);
    } catch (Throwable ex) {
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  public void uninstallApp(final String appName, final ServiceResponseListener<UninstallApp.Response> callback) {
    try {
      ServiceClient<UninstallApp.Request, UninstallApp.Response> uninstallAppClient =
        node.newServiceClient(resolver.resolve("uninstall_app"), "app_manager/UninstallApp");
      UninstallApp.Request request = new UninstallApp.Request();
      request.name = appName;
      uninstallAppClient.call(request, callback);
    } catch (Throwable ex) {
      callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
    }
  }
  
  /**
   * Blocks until App Manager is located.
   *
   * @param node
   * @param robotName
   * @return
   * @throws AppManagerNotAvailableException
   * @throws RosException
   */
  public static AppManager create(Node node, String robotName) throws XmlRpcTimeoutException,
      AppManagerNotAvailableException, RosException {
    NameResolver resolver = node.getResolver().newChild(new GraphName(robotName));
    try {
      return new AppManager(node, resolver);
    } catch( java.lang.RuntimeException ex ) {
      throw new AppManagerNotAvailableException( ex );
    }
  }
}
