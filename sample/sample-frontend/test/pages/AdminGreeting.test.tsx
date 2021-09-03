import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpConnection, HttpLocation, Prefix } from '@tmtsoftware/esw-ts'
import { expect } from 'chai'
import React from 'react'
import { anything, capture, deepEqual, verify, when } from 'ts-mockito'
import { AdminGreeting } from '../../src/components/pages/AdminGreeting'
import {
  locationServiceMock,
  mockFetch,
  renderWithRouter
} from '../utils/test-utils'

describe('AdminGreeting', () => {
  const connection = HttpConnection(Prefix.fromString('ESW.sample'), 'Service')

  const httpLocation: HttpLocation = {
    _type: 'HttpLocation',
    uri: 'some-backend-url',
    connection,
    metadata: {}
  }
  when(locationServiceMock.find(deepEqual(connection))).thenResolve(
    httpLocation
  )

  it('should render admin Input form and display message on submit', async () => {
    const firstName = 'John'
    const lastName = 'Smith'
    const userInfo = {
      firstName,
      lastName
    }
    const greeting = `Hello admin user: ${firstName} ${lastName}!!!`
    const response = new Response(JSON.stringify({ greeting }))
    const fetch = mockFetch()

    when(fetch(anything(), anything())).thenResolve(response)

    renderWithRouter(<AdminGreeting />)

    const firstNameInput = (await screen.findByRole(
      'FirstName'
    )) as HTMLInputElement
    const lastNameInput = (await screen.findByRole(
      'LastName'
    )) as HTMLInputElement

    userEvent.type(firstNameInput, firstName)
    userEvent.type(lastNameInput, lastName)

    const submitButton = (await screen.findByRole(
      'Submit'
    )) as HTMLButtonElement

    await waitFor(() => userEvent.click(submitButton))
    debugger
    verify(locationServiceMock.find(deepEqual(connection))).called()
    const [firstArg, secondArg] = capture(fetch).last()
    expect(firstArg).to.equal(httpLocation.uri + 'adminGreeting')

    const expectedReq = {
      method: 'POST',
      body: JSON.stringify(userInfo),
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer token string'
      }
    }

    expect(JSON.stringify(secondArg)).to.equal(JSON.stringify(expectedReq))

    await screen.findByText(greeting)
  })
})
