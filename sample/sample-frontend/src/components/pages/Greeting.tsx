import React, { useState } from 'react'
import { useLocationService } from '../../contexts/LocationServiceContext'
import type { UserInfo } from '../../models/Models'
import { fetchGreeting } from '../../utils/api'
import { getBackendUrl } from '../../utils/resolveBackend'
import { UserForm } from '../form/UserForm'
import { displayMessage } from './Welcome'

export const Greeting = (): JSX.Element => {
  const [greeting, setGreeting] = useState<string>()

  const locationService = useLocationService()

  const onFinish = async (values: UserInfo) => {
    const backendUrl = await getBackendUrl(locationService)

    if (backendUrl) {
      const response = await fetchGreeting(backendUrl, values)
      if (response?.greeting) setGreeting(response.greeting)
      else {
        console.error(response)
        throw new Error('Invalid response, greeting field is missing')
      }
    }
  }

  return (
    <>
      <UserForm onFinish={onFinish} />
      {greeting && displayMessage(greeting)}
    </>
  )
}
