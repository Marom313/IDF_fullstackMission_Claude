import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { errorMessage } from '../../../shared/api/errors';
import { useAuth } from '../../../shared/auth/AuthContext';
import { translateReason } from '../../../shared/i18n/format';
import { useRequiredFields, useSubmitApplication, type PermitType } from '../model/applyApi';

/** Fields the server fills from the profile/registry – shown as info, not typed by the user. */
const FROM_PROFILE = new Set(['idNumber', 'photo']);
const DATE_FIELDS = new Set(['appointmentDate']);
const REGION_FIELDS = new Set(['workRegion', 'destinationRegion']);
export const REGIONS = ['JERUSALEM', 'TEL_AVIV', 'CENTER', 'SHARON', 'HAIFA', 'NORTH', 'SHFELA', 'SOUTH'];

/**
 * Dynamic form: the Zod schema is BUILT from the policy's REQUIRE FIELDS. React Hook Form is
 * uncontrolled, so typing in one field does not re-render the other fields.
 */
export function useApplyViewModel() {
  const { t, i18n } = useTranslation();
  const { hasAny } = useAuth();
  const [type, setType] = useState<PermitType>(hasAny('DRIVER') ? 'DRIVER' : 'WORK_ENTRY');
  const required = useRequiredFields(type);
  const submit = useSubmitApplication();
  const idemKey = useRef(crypto.randomUUID()); // one key per form instance → safe retries
  const onBehalf = hasAny('EMPLOYER', 'CLERK');

  const inputFields = useMemo(() => (required.data ?? []).filter((f) => !FROM_PROFILE.has(f)), [required.data]);

  const schema = useMemo(() => {
    const shape: Record<string, z.ZodTypeAny> = {};
    for (const f of inputFields) shape[f] = z.string().trim().min(1, t('field.required'));
    shape.applicantId = onBehalf
      ? z.string().regex(/^\d{9}$/, t('crossing.enterId'))
      : z.string().optional();
    return z.object(shape);
  }, [inputFields, onBehalf, t]);

  const form = useForm<Record<string, string>>({ resolver: zodResolver(schema), mode: 'onBlur' });

  const onSubmit = form.handleSubmit((values) => {
    const { applicantId, ...formData } = values;
    submit.mutate({
      req: { type, nationalId: onBehalf ? applicantId : undefined, formData },
      idempotencyKey: idemKey.current,
    }, { onSuccess: () => { idemKey.current = crypto.randomUUID(); form.reset(); } });
  });

  const result = submit.data;
  return {
    type, setType: (v: PermitType) => { setType(v); form.reset(); submit.reset(); },
    types: ['WORK_ENTRY', 'DRIVER', 'MEDICAL'] as PermitType[],
    onBehalf,
    loadingFields: required.isLoading,
    fields: inputFields.map((name) => ({
      name,
      label: t(`field.${name}`, { defaultValue: name }),
      kind: DATE_FIELDS.has(name) ? 'date' : REGION_FIELDS.has(name) ? 'region' : 'text',
      error: form.formState.errors[name]?.message as string | undefined,
    })),
    profileFields: (required.data ?? []).filter((f) => FROM_PROFILE.has(f)).map((f) => t(`field.${f}`)),
    applicantError: form.formState.errors.applicantId?.message as string | undefined,
    register: form.register,
    onSubmit,
    submitting: submit.isPending,
    error: submit.error ? errorMessage(submit.error, t) : null,
    submitted: result ? {
      id: result.id ?? '',
      recommendation: t(`decision.${result.recommendation}`, { defaultValue: result.recommendation ?? '' }),
      reasons: (result.reasons ?? []).map((r) => translateReason(r, t, i18n.language)),
      policyVersion: result.policyVersion ?? '',
    } : null,
  };
}
