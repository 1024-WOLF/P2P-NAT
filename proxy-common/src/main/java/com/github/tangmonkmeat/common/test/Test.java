package com.github.tangmonkmeat.common.test;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Description:
 *
 * @author zwl
 * @version 1.0
 * @date 2021/2/21 22:37
 */
public class Test {
    public static void main(String[] args) {
        //// 定义线程1
        //Thread thread1 = new Thread() {
        //    @Override
        //    public void run() {
        //        System.out.println("thread1...");
        //    }
        //};
        //
        //// 定义线程2
        //Thread thread2 = new Thread() {
        //    @Override
        //    public void run() {
        //        System.out.println("thread2...");
        //    }
        //};
        //
        //// 定义关闭线程
        //Thread shutdownThread = new Thread() {
        //    @Override
        //    public void run() {
        //        System.out.println("shutdownThread...");
        //    }
        //};
        //
        //// jvm关闭的时候先执行该线程钩子
        //Runtime.getRuntime().addShutdownHook(shutdownThread);
        //
        //thread1.start();
        //thread2.start();

        ConcurrentLinkedQueue<Channel> proxyChannelPool = new ConcurrentLinkedQueue<Channel>();
        Channel poll = proxyChannelPool.poll();
        System.out.println(poll);
    }
}
