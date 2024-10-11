///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { EntityComponent } from '@home/components/entity/entity.component';
import { AppState } from '@core/core.state';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { FormBuilder, FormGroup, UntypedFormControl, Validators } from '@angular/forms';
import { randomAlphanumeric } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { MobileApp, MobileAppStatus, mobileAppStatusTranslations } from '@shared/models/mobile-app.models';
import { PlatformType, platformTypeTranslations } from '@shared/models/oauth2.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-mobile-app',
  templateUrl: './mobile-app.component.html',
  styleUrls: ['./mobile-app.component.scss']
})
export class MobileAppComponent extends EntityComponent<MobileApp> {

  entityType = EntityType;

  platformTypes = [PlatformType.ANDROID, PlatformType.IOS];

  MobileAppStatus = MobileAppStatus;
  PlatformType = PlatformType;

  mobileAppStatuses = Object.keys(MobileAppStatus) as MobileAppStatus[];

  platformTypeTranslations = platformTypeTranslations;
  mobileAppStatusTranslations = mobileAppStatusTranslations;

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Inject('entity') protected entityValue: MobileApp,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<MobileApp>,
              protected cd: ChangeDetectorRef,
              public fb: FormBuilder) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: MobileApp): FormGroup {
    const form = this.fb.group({
      pkgName: [entity?.pkgName ? entity.pkgName : '', [Validators.required, Validators.maxLength(255),
        Validators.pattern(/^\S+$/)]],
      platformType: [entity?.platformType ? entity.platformType : PlatformType.ANDROID],
      appSecret: [entity?.appSecret ? entity.appSecret : btoa(randomAlphanumeric(64)), [Validators.required, this.base64Format]],
      status: [entity?.status ? entity.status : MobileAppStatus.DRAFT],
      versionInfo: this.fb.group({
        minVersion: [entity?.versionInfo?.minVersion ? entity.versionInfo.minVersion : ''],
        latestVersion: [entity?.versionInfo?.latestVersion ? entity.versionInfo.latestVersion : ''],
      }),
      storeInfo: this.fb.group({
        storeLink: [entity?.storeInfo?.storeLink ? entity.storeInfo.storeLink : ''],
        sha256CertFingerprints: [entity?.storeInfo?.sha256CertFingerprints ? entity.storeInfo.sha256CertFingerprints : ''],
        appId: [entity?.storeInfo?.appId ? entity.storeInfo.appId : ''],
      }),
    });

    form.get('platformType').valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe((value: PlatformType) => {
      if (value === PlatformType.ANDROID) {
        form.get('storeInfo.sha256CertFingerprints').enable({emitEvent: false});
        form.get('storeInfo.appId').disable({emitEvent: false});
      } else if (value === PlatformType.IOS) {
        form.get('storeInfo.sha256CertFingerprints').disable({emitEvent: false});
        form.get('storeInfo.appId').enable({emitEvent: false});
      }
      form.get('storeInfo.storeLink').setValue('', {emitEvent: false});
    });

    form.get('status').valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe((value: MobileAppStatus) => {
      if (value === MobileAppStatus.PUBLISHED) {
        form.get('storeInfo.storeLink').addValidators(Validators.required);
        form.get('storeInfo.sha256CertFingerprints').addValidators(Validators.required);
        form.get('storeInfo.appId').addValidators(Validators.required);
      } else {
        form.get('storeInfo.storeLink').clearValidators();
        form.get('storeInfo.sha256CertFingerprints').clearValidators();
        form.get('storeInfo.appId').clearValidators();
      }
      form.get('storeInfo.storeLink').updateValueAndValidity({emitEvent: false});
      form.get('storeInfo.sha256CertFingerprints').updateValueAndValidity({emitEvent: false});
      form.get('storeInfo.appId').updateValueAndValidity({emitEvent: false});
    });

    return form;
  }

  updateForm(entity: MobileApp) {
    this.entityForm.patchValue(entity, {emitEvent: false});
  }

  override updateFormState(): void {
    super.updateFormState();
    if (this.isEdit && this.entityForm && !this.isAdd) {
      this.entityForm.get('platformType').disable({emitEvent: false});
      if (this.entityForm.get('platformType').value === PlatformType.ANDROID) {
        this.entityForm.get('storeInfo.appId').disable({emitEvent: false});
      } else if (this.entityForm.get('platformType').value === PlatformType.IOS) {
        this.entityForm.get('storeInfo.sha256CertFingerprints').disable({emitEvent: false});
      }
    }
    if (this.entityForm && this.isAdd) {
      this.entityForm.get('storeInfo.appId').disable({emitEvent: false});
    }
  }

  override prepareFormValue(value: MobileApp): MobileApp {
    value.storeInfo = this.entityForm.get('storeInfo').value;
    return super.prepareFormValue(value);
  }

  generateAppSecret($event: Event) {
    $event.stopPropagation();
    this.entityForm.get('appSecret').setValue(btoa(randomAlphanumeric(64)));
    this.entityForm.get('appSecret').markAsDirty();
  }

  private base64Format(control: UntypedFormControl): { [key: string]: boolean } | null {
    if (control.value === '') {
      return null;
    }
    try {
      const value = atob(control.value);
      if (value.length < 64) {
        return {minLength: true};
      }
      return null;
    } catch (e) {
      return {base64: true};
    }
  }
}
