import { IKRRouterAdapter, KRRecord } from '@kuikly-open/render';
import router from '@ohos.router';
import common from '@ohos.app.ability.common';

export class RouterAdapter implements IKRRouterAdapter {
  openPage(context: common.UIAbilityContext, moduleName: string, pageName: string, pageData: KRRecord): void {
    router.pushUrl({
      url: 'pages/Index',
      params: {
        moduleName,
        pageName,
        pageData
      }
    })
  }

  closePage(context: common.UIAbilityContext): void {
    router.back()
  }
}