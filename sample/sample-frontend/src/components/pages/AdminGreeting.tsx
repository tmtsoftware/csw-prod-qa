import React, { useState } from 'react'
import { useLocationService } from '../../contexts/LocationServiceContext'
import { useAuth } from '../../hooks/useAuth'
import type { UserInfo } from '../../models/Models'
import { fetchAdminGreeting } from '../../utils/api'
import { errorMessage } from '../../utils/message'
import { getBackendUrl } from '../../utils/resolveBackend'
import { UserForm } from '../form/UserForm'
import { displayMessage } from './Welcome'

export const AdminGreeting = (): JSX.Element => {
  const { auth } = useAuth()

  const [greeting, setGreeting] = useState<string>()

  const locationService = useLocationService()

  const onFinish = async (values: UserInfo) => {
    const backendUrl = await getBackendUrl(locationService)

    if (backendUrl) {
      const token = auth?.token()
      if (!token) {
        errorMessage('Failed to greet user: Unauthenticated request')
      } else {
        const response = await fetchAdminGreeting(backendUrl, values, token)
        if (response?.greeting) setGreeting(response.greeting)
        else {
          console.error(response)
          throw new Error('Invalid response, greeting field is missing')
        }
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
