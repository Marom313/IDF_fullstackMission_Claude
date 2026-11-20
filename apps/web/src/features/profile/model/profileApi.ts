import { useQuery } from '@tanstack/react-query';
import { http, httpBlobUrl } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { PermitDto, PersonDto, QrBundle } from '../../../shared/api/types';

export const useMe = () => useQuery({
  queryKey: qk.me, staleTime: staleTimes.profile, queryFn: () => http<PersonDto>('/api/registry/persons/me'),
});

export const useMyPermits = () => useQuery({
  queryKey: qk.myPermits, staleTime: staleTimes.list, queryFn: () => http<PermitDto[]>('/api/permits/mine'),
});

export const usePhoto = (url?: string) => useQuery({
  queryKey: ['photo', url], enabled: !!url, staleTime: Infinity, queryFn: () => httpBlobUrl(url!),
});

export const fetchQr = (permitId: string) => http<QrBundle>(`/api/permits/${permitId}/qr`);
export const fetchPdfUrl = (permitId: string) => httpBlobUrl(`/api/permits/${permitId}/pdf`);
