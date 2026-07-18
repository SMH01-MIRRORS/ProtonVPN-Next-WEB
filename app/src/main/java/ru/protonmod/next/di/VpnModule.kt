/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.protonmod.next.vpn.AwgBoxConfigGenerator
import ru.protonmod.next.vpn.AwgBoxConfigGeneratorImpl
import ru.protonmod.next.vpn.IpSubnetCalculator
import ru.protonmod.next.vpn.IpSubnetCalculatorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {

    @Binds
    @Singleton
    abstract fun bindAwgBoxConfigGenerator(
        impl: AwgBoxConfigGeneratorImpl
    ): AwgBoxConfigGenerator

    @Binds
    @Singleton
    abstract fun bindIpSubnetCalculator(
        impl: IpSubnetCalculatorImpl
    ): IpSubnetCalculator
}
