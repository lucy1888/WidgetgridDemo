/*
 * Tencent is pleased to support the open source community by making KuiklyUI
 * available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 * Licensed under the License of KuiklyUI;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://github.com/Tencent-TDS/KuiklyUI/blob/main/LICENSE
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {
  IKRPAGViewAdapter,
  IKRPAGViewController,
  IKRPAGViewListener,
  KRArray,
  KRPAGView,
  KRValue,
  KuiklyRenderBaseView,
  KuiklyRenderCallback
} from '@kuikly-open/render';
import { UIContext } from '@ohos.arkui.UIContext';
import { ComponentContent } from '@kit.ArkUI';
import {
  PAGFile,
  PAGImage,
  PAGImageLayer,
  PAGLayerType,
  PAGTextLayer,
  PAGView,
  PAGViewController,
  PAGViewListener
} from '@tencent/libpag';

@Component
struct MyPAGView {
  @State pagViewController: PAGViewController | null = null;

  onPageShow(): void {
    if (this.pagViewController) {
      this.pagViewController.setSync(true)
      this.pagViewController.setCacheEnabled(false)
    }
  }

  onPageHide(): void {
    if (this.pagViewController) {
      this.pagViewController.freeCache()
    }
  }

  build() {
    PAGView({
      controller: this.pagViewController
    });
  }
}

@Builder
function createMyPAGView(view: KuiklyRenderBaseView) {
  MyPAGView({
    pagViewController: ((view as KRPAGView).controller! as AppKRPAGViewController).viewController!,
  });
}

class AppKRPAGViewController implements IKRPAGViewController, PAGViewListener {
  viewController: PAGViewController = new PAGViewController();
  cachedUpdateContent: KRArray = [];
  listeners = new Set<IKRPAGViewListener>();
  myself = new WeakRef(this);
  onAnimationStart = (viewController: PAGViewController) => {
    this.listeners.forEach((listener) => {
      if (listener.onAnimationStart) {
        listener.onAnimationStart(this);
      }
    });
  };
  onAnimationEnd = (viewController: PAGViewController) => {
    this.listeners.forEach((listener) => {
      if (listener.onAnimationEnd) {
        listener.onAnimationEnd(this);
      }
    });
  };
  onAnimationRepeat = (viewController: PAGViewController) => {
    this.listeners.forEach((listener) => {
      if (listener.onAnimationRepeat) {
        listener.onAnimationRepeat(this);
      }
    });
  };
  onAnimationCancel = (viewController: PAGViewController) => {
    this.listeners.forEach((listener) => {
      if (listener.onAnimationCancel) {
        listener.onAnimationCancel(this);
      }
    });
  };
  onAnimationUpdate = (viewController: PAGViewController) => {
    this.listeners.forEach((listener) => {
      if (listener.onAnimationUpdate) {
        listener.onAnimationUpdate(this);
      }
    });
  };

  addListener(listener: IKRPAGViewListener): void {
    this.listeners.add(listener);
    this.viewController.addListener(this.myself);
  }

  removeListener(listener: IKRPAGViewListener): void {
    this.listeners.delete(listener);
  }

  play(): void {
    this.viewController.play();
  }

  stop(): void {
    this.viewController.pause();
  }
  setProgress(value: number): void {
    this.viewController.setProgress(value)
  }

  setProp(propKey: string, propValue: KRValue | KuiklyRenderCallback): boolean {
    console.error(`AppKRPAGViewController propKey=${propKey} propValue=${JSON.stringify(propValue)}`)
    if (propKey == 'src') {
      const resdir = getContext().resourceDir;
      const srcValue = propValue.toString();
      if (srcValue.startsWith('assets://')) {
        const trailing = srcValue.substring(8);
        const pathname = resdir + trailing;
        if (this.viewController?.setPath(pathname)) {
        } else {
          this.LoadAssetsFile(pathname);
        }
      } else {
        this.LoadFromPathAsync(srcValue);
      }
    } else if (propKey == 'autoPlay' && typeof (propValue) === 'number') {
      this.viewController.setVideoEnabled((propValue as number) != 0);
    } else if (propKey == 'repeatCount' && typeof (propValue) === 'number') {
      this.viewController.setRepeatCount(propValue as number);
    } else if (propKey == 'scaleMode' && typeof (propValue) === 'number') {
      this.viewController.setScaleMode(propValue as number);
    } else if (propKey == 'replaceTextLayerContent' && typeof (propValue) === 'string') {
      const array = propValue.split(',')
      const viewName = array[0]
      const textValue = array[1]
      const layer = this.viewController.getComposition()?.getLayersByName(viewName);
      if (layer && layer.length > 0) {
        layer.forEach((curLayer)=>{
          if (curLayer.layerType() == PAGLayerType.Text) {
            (curLayer as PAGTextLayer).setText(textValue)
            this.viewController.flush()
          }
        })
      }
    }  else if (propKey == 'replaceImageLayerContent' && typeof (propValue) === 'string') {
      const array = propValue.split(',')
      const viewName = array[0]
      const srcValue = array[1]
      const resdir = getContext().resourceDir;
      const layer = this.viewController.getComposition()?.getLayersByName(viewName);
      if (layer && layer.length > 0) {
        layer.forEach((curLayer)=>{
          if (curLayer.layerType() == PAGLayerType.Image) {
            if (srcValue.startsWith('assets://')) {
              const trailing = srcValue.substring(8);
              const pathname = resdir + trailing;
              const image = PAGImage.FromPath(pathname)
              if (image) {
                (curLayer as PAGImageLayer).setImage(image)
                this.viewController.flush()
              }
            } else {
              console.error(`AppKRPAGViewController replaceImageLayerContent viewName=${viewName} 文件路径异常=${srcValue}`)
            }
          } else {
            console.error(`AppKRPAGViewController replaceImageLayerContent viewName=${viewName} 文件类型不对=${curLayer.layerType()}`)
          }
        })
      } else {
        console.error(`AppKRPAGViewController replaceImageLayerContent viewName=${viewName}未匹配到PAGImageLayer`)
      }

    }

    return false;
  }

  async LoadAssetsFile(imageFileAsset: string): Promise<void> {
    const file = PAGFile.LoadFromPath(imageFileAsset)
    this.viewController.setComposition(file)
    this.viewController.play()
  }

  async LoadFromPathAsync(url: string): Promise<void> {
    let file = await PAGFile.LoadFromPathAsync(url);
    this.viewController.setComposition(file);
    this.viewController.play();
  }
}

export class AppKRPagViewAdapter implements IKRPAGViewAdapter {
  createController(): IKRPAGViewController {
    return new AppKRPAGViewController();
  }

  createPAGView(ctx: UIContext, view: KuiklyRenderBaseView): ComponentContent<KuiklyRenderBaseView> {
    return new ComponentContent<KuiklyRenderBaseView>(ctx, wrapBuilder<[KuiklyRenderBaseView]>(createMyPAGView), view);
  }
}