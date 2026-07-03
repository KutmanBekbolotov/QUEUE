import { Module } from '@nestjs/common';
import { BackendClientModule } from '../backend-client/backend-client.module';

@Module({
  imports: [BackendClientModule],
})
export class BookingProxyModule {}

