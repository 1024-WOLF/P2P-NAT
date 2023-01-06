package org.github.$1024wolf;

import org.github.$1024wolf.common.Container.ContainerHelper;
import org.github.$1024wolf.core.ProxyServerContainer;
import org.github.$1024wolf.web.WebConfigContainer;

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
        //System.out.println(Runtime.getRuntime().availableProcessors());
    }
}
