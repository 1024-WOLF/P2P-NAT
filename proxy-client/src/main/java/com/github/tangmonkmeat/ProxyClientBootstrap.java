package com.github.tangmonkmeat;

import com.github.tangmonkmeat.common.Container.Container;
import com.github.tangmonkmeat.common.Container.ContainerHelper;
import com.github.tangmonkmeat.core.ProxyClientContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Description:
 * 代理客户端启动器
 * @author zwl
 * @version 1.0
 * @date 2021/2/26 下午10:18
 */
public class ProxyClientBootstrap {
    public static void main(String[] args) {
        List<Container> containers = new ArrayList<>(1);
        containers.add(new ProxyClientContainer());
        ContainerHelper.start(containers);
    }
}
