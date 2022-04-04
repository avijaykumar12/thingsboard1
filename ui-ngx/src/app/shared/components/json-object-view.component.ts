///
/// Copyright © 2016-2022 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, ElementRef, forwardRef, Input, OnInit, Renderer2, ViewChild } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { Ace } from 'ace-builds';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { RafService } from '@core/services/raf.service';
import { isDefinedAndNotNull, isObject, isUndefined } from '@core/utils';
import { getAce } from '@shared/models/ace/ace.models';

@Component({
  selector: 'tb-json-object-view',
  templateUrl: './json-object-view.component.html',
  styleUrls: ['./json-object-view.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => JsonObjectViewComponent),
      multi: true
    }
  ]
})
export class JsonObjectViewComponent implements OnInit {

  @ViewChild('jsonViewer', {static: true})
  jsonViewerElmRef: ElementRef;

  private jsonViewer: Ace.Editor;
  private viewerElement: Ace.Editor;
  private propagateChange = null;
  private contentValue: string;

  @Input() label: string;

  @Input() fillHeight: boolean;

  @Input() editorStyle: { [klass: string]: any };

  @Input() sort: (key: string, value: any) => any;

  private widthValue: boolean;

  get autoWidth(): boolean {
    return this.widthValue;
  }

  @Input()
  set autoWidth(value: boolean) {
    this.widthValue = coerceBooleanProperty(value);
  }

  private heightValue: boolean;

  get autoHeight(): boolean {
    return this.heightValue;
  }

  @Input()
  set autoHeight(value: boolean) {
    this.heightValue = coerceBooleanProperty(value);
  }

  constructor(public elementRef: ElementRef,
              private raf: RafService,
              private renderer: Renderer2) {
  }

  ngOnInit(): void {
    this.viewerElement = this.jsonViewerElmRef.nativeElement;
    let editorOptions: Partial<Ace.EditorOptions> = {
      mode: 'ace/mode/java',
      theme: 'ace/theme/github',
      showGutter: false,
      showPrintMargin: false,
      readOnly: true
    };

    const advancedOptions = {
      enableSnippets: false,
      enableBasicAutocompletion: false,
      enableLiveAutocompletion: false
    };

    editorOptions = {...editorOptions, ...advancedOptions};
    getAce('java', 'github').subscribe(
      (ace) => {
        this.jsonViewer = ace.edit(this.viewerElement, editorOptions);
        this.jsonViewer.session.setUseWrapMode(false);
        this.jsonViewer.setValue(this.contentValue ? this.contentValue : '', -1);
        if (this.contentValue && (this.autoWidth || this.autoHeight)) {
          this.updateEditorSize(this.viewerElement, this.contentValue, this.jsonViewer);
        }
      }
    );
  }

  updateEditorSize(editorElement: any, content: string, editor: Ace.Editor) {
    let newHeight = 200;
    let newWidth = 600;
    if (content && content.length > 0) {
      const lines = content.split('\n');
      newHeight = 17 * lines.length + 17;
      let maxLineLength = 0;
      lines.forEach((row) => {
        const line = row.replace(/\t/g, '    ').replace(/\n/g, '');
        const lineLength = line.length;
        maxLineLength = Math.max(maxLineLength, lineLength);
      });
      newWidth = 8 * maxLineLength + 16;
    }
    if (this.autoHeight) {
      this.renderer.setStyle(editorElement, 'height', newHeight.toString() + 'px');
    }
    if (this.autoWidth) {
      this.renderer.setStyle(editorElement, 'width', newWidth.toString() + 'px');
    }
    editor.resize();
  }
  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  writeValue(value: any): void {
    this.contentValue = value;
    try {
      if (isDefinedAndNotNull(value) && isObject(value)) {
        this.contentValue = JSON.stringify(value, isUndefined(this.sort) ? undefined :
          (key, objectValue) => {
            return this.sort(key, objectValue);
          }, 2);
      }
    } catch (e) {
      console.error(e);
    }
    if (this.jsonViewer) {
      this.jsonViewer.setValue(this.contentValue ? this.contentValue : '', -1);
      if (this.contentValue && (this.autoWidth || this.autoHeight)) {
        this.updateEditorSize(this.viewerElement, this.contentValue, this.jsonViewer);
      }
    }
  }

}
