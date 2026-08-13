/*
  role.model.ts
  Mirrors auth-service's Role/PartnerType Java enums (both EnumType.STRING)
  as TypeScript string literal unions
*/

export type Role = 'TRAVELER' | 'PARTNER' | 'ADMIN';

// null when role !== 'PARTNER' (a traveler/admin account has no partner type)
export type PartnerType = 'HOTEL' | 'FLIGHT' | null;
