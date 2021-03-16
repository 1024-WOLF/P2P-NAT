package com.github.tangmonkmeat;

import com.github.tangmonkmeat.common.Container.ContainerHelper;
import com.github.tangmonkmeat.core.ProxyServerContainer;
import com.github.tangmonkmeat.web.WebConfigContainer;

import java.util.Arrays;

/**
 * Description:
 *
 * @author zwl
 * @version 1.0
 * @date 2021/3/2 下午1:10
 */
public class ProxyServerBootstrap {

    public static void main(String[] args) {
        ContainerHelper.start(Arrays.asList(new ProxyServerContainer(), new WebConfigContainer()));
        //System.out.println(ProxyServerBootstrap .class.getResource("/pages").toURI().getPath());
    }
}
